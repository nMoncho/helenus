/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import _root_.net.nmoncho.helenus.api.cql.ScalaBoundStatement
import _root_.net.nmoncho.helenus.internal.cql.ScalaPreparedStatement2
import _root_.org.apache.pekko.Done
import _root_.org.apache.pekko.NotUsed
import _root_.org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import _root_.org.apache.pekko.stream.scaladsl.Flow
import _root_.org.apache.pekko.stream.scaladsl.Keep
import _root_.org.apache.pekko.stream.scaladsl.RunnableGraph
import _root_.org.apache.pekko.stream.scaladsl.Sink
import _root_.org.apache.pekko.stream.scaladsl.Source
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.api.core.metadata.token.TokenRange

/** Pekko executor for a token-range full-table scan.
  *
  * Turns a [[RingPlan]] into a Pekko `Source`, reading several ranges concurrently
  * so throughput is decoupled from the number of nodes. Each range is bound with
  * the core token codec (see [[TokenRangePlanner]]) and carries a routing token, so
  * the driver sends the query to an owning replica. A range that times out is
  * retried by splitting it into halves (see [[RetryPolicy]]).
  */
package object pekko {

  implicit class TokenRangePekkoReadOps[Out](
      private val pstmt: ScalaPreparedStatement2[Token, Token, Out]
  ) extends AnyVal {

    /** Reads a whole table by scanning every range in `plan`.
      *
      * The prepared statement must be a bounded token-range query shaped like
      * `SELECT ... WHERE token(pk) > ? AND token(pk) <= ?`. Up to `parallelism`
      * ranges are read concurrently via `flatMapMerge`, so a single process can
      * saturate the cluster rather than reading one range at a time. Results are
      * emitted as they arrive and are therefore unordered.
      *
      * There is no default rate cap: with `rateLimit` left as `None` the scan runs
      * as fast as the cluster and backpressure allow. Pass a [[RateLimit]] only to
      * deliberately shape the rate.
      *
      * A range that times out is retried by splitting it into halves per `retry`.
      * A retry re-reads the range from the start, so for a range that had already
      * emitted rows the retry may re-emit them; this is safe for an idempotent
      * (upsert) load, which is the migration case.
      *
      * Ranges already recorded in `checkpoint` are skipped, and each range is marked
      * completed once it has been fully read, so a restart resumes rather than
      * re-scanning (see [[Checkpoint]]).
      *
      * Pass `executionProfile` to run the reads under a named driver execution
      * profile, for example a lower read consistency for throughput. The write side
      * sets its own profile on its sink, so read and write consistency stay
      * independent (a common pattern is `LOCAL_ONE` reads and `LOCAL_QUORUM` writes).
      *
      * @param plan             the ring scan plan from [[TokenRangePlanner]]
      * @param parallelism      how many ranges to read at once (defaults to the
      *                         number of available processors); below 1 means 1
      * @param rateLimit        an optional, opt-in cap on emitted rows; `None` = no cap
      * @param retry            how to retry a range that times out (defaults to
      *                         halving it up to four times)
      * @param checkpoint which ranges to skip and where to record completions
      *                   (defaults to [[Checkpoint.None]], recording nothing)
      * @param executionProfile optional driver execution profile name for the read
      *                         statements; `None` uses the session default
      * @param metrics          observability callback for extracted rows, range
      *                         completions, and range failures (defaults to a no-op)
      * @param routing          set a token-aware routing token on each range query so
      *                         the driver hits an owning replica (defaults to true;
      *                         disable only to measure or work around routing)
      */
    def asTokenRangeReadSource(
        plan: RingPlan,
        parallelism: Int                 = DefaultParallelism,
        rateLimit: Option[RateLimit]     = None,
        retry: RetryPolicy               = RetryPolicy.Default,
        checkpoint: Checkpoint           = Checkpoint.None,
        executionProfile: Option[String] = None,
        metrics: MigrationMetrics        = MigrationMetrics.none,
        routing: Boolean                 = true
    )(implicit session: CassandraSession): Source[Out, NotUsed] = {
      val progress = new AtomicReference(RingProgress(plan))

      val scanned =
        if (plan.isEmpty) {
          Source.empty[Out]
        } else {
          Source(plan.splits)
            .flatMapMerge(
              Math.max(1, parallelism),
              scanSplit(pstmt, _, retry, checkpoint, executionProfile, routing, metrics, progress)
            )
        }

      val source = scanned
        .map { row => metrics.rowExtracted(); row }
        .mapError { case error => metrics.rangeFailed(error); error }

      rateLimit.fold(source)(limit => source.throttle(limit.elements, limit.per))
    }

    /** Composes a whole Cassandra to Cassandra migration as a runnable graph: read the
      * table with [[asTokenRangeReadSource]], apply `transform`, and write with `sink`.
      *
      * All the executor read options are forwarded, and the load side is counted via
      * `metrics.rowLoaded`, so one call wires extract, transform, load, and full
      * observability. For a plain function transform pass `Flow[Out].map(f)`; for a
      * filtering transform use `collect`/`mapConcat`, in which case fewer rows load
      * than are extracted. Call `.run()` on the result with a `Materializer` (or an
      * `ActorSystem`) in scope.
      *
      * With `dryRun = true` the load `sink` is replaced by a discarding sink, so the
      * migration reads and transforms (and counts, so `metrics` still reports the
      * extracted and transformed rows) but writes nothing. This validates the extract
      * and transform without touching the target.
      *
      * @param transform how to turn each read row into a row to write
      * @param sink      the write sink, typically `...prepareFrom[...].asWriteSink(...)`
      * @param dryRun    when true, do not write; read, transform, and count only
      */
    def asTokenRangeMigration[B](
        plan: RingPlan,
        transform: Flow[Out, B, NotUsed],
        sink: Sink[B, Future[Done]],
        parallelism: Int                 = DefaultParallelism,
        rateLimit: Option[RateLimit]     = None,
        retry: RetryPolicy               = RetryPolicy.Default,
        checkpoint: Checkpoint           = Checkpoint.None,
        executionProfile: Option[String] = None,
        metrics: MigrationMetrics        = MigrationMetrics.none,
        dryRun: Boolean                  = false
    )(implicit session: CassandraSession): RunnableGraph[Future[Done]] =
      asTokenRangeReadSource(
        plan,
        parallelism,
        rateLimit,
        retry,
        checkpoint,
        executionProfile,
        metrics
      )
        .via(transform)
        .map { loaded => metrics.rowLoaded(); loaded }
        .toMat(if (dryRun) Sink.ignore else sink)(Keep.right)

    /** Binds one [[RangeSplit]] into a routing-aware bound statement, optionally under
      * a named execution profile. Exposed for testing and advanced use; see
      * [[boundForRange]] for the details.
      */
    def tokenRangeStatement(
        split: RangeSplit,
        executionProfile: Option[String] = None,
        routing: Boolean                 = true
    ): ScalaBoundStatement[Out] =
      boundForRange(pstmt, split.range, executionProfile, routing)
  }

  /** Reads one split: skips it when the checkpoint already has it, otherwise reads
    * it (with timeout retries) and marks it completed once fully read. Completion is
    * recorded by concatenating a lazy, empty source so the mark runs in-stream after
    * the range finishes successfully, and never on failure (`concat` does not run its
    * second source if the first fails).
    */
  private def scanSplit[Out](
      pstmt: ScalaPreparedStatement2[Token, Token, Out],
      split: RangeSplit,
      retry: RetryPolicy,
      checkpoint: Checkpoint,
      executionProfile: Option[String],
      routing: Boolean,
      metrics: MigrationMetrics,
      progress: AtomicReference[RingProgress]
  )(implicit session: CassandraSession): Source[Out, NotUsed] =
    if (checkpoint.isCompleted(split)) {
      // Already done on a previous run: advance progress but do not re-read.
      metrics.rangeCompleted(progress.updateAndGet(_.completing(split)))
      Source.empty[Out]
    } else {
      readWithRetry(split.range, depth = 0, retry)(range =>
        rangeReadSource(boundForRange(pstmt, range, executionProfile, routing))
      ).concat(Source.lazySource { () =>
        checkpoint.markCompleted(split)
        metrics.rangeCompleted(progress.updateAndGet(_.completing(split)))
        Source.empty[Out]
      })
    }

  /** Reads a single range, retrying on timeout by splitting it into halves.
    *
    * On a retryable failure ([[RetryPolicy.isRetryable]]) the range is halved and
    * each half is read (recursively) at the next depth. When the range cannot be
    * split further or `depth` reaches `retry.maxSplits`, the failure is surfaced as
    * a [[TokenRangeReadException]] naming the range. `read` is a parameter so the
    * split-and-retry orchestration is unit-testable with an injected failing read.
    */
  private[pekko] def readWithRetry[Out](
      range: TokenRange,
      depth: Int,
      retry: RetryPolicy
  )(read: TokenRange => Source[Out, NotUsed]): Source[Out, NotUsed] =
    read(range).recoverWithRetries(
      attempts = 1,
      {
        case ex if RetryPolicy.isRetryable(ex) =>
          val halves = if (depth < retry.maxSplits) TokenRing.halve(range) else Vector.empty

          if (halves.size >= 2) {
            Source(halves).flatMapConcat(half => readWithRetry(half, depth + 1, retry)(read))
          } else {
            Source.failed[Out](new TokenRangeReadException(range, ex))
          }
      }
    )

  /** Reads a single already-bound range statement as a `Source`, mirroring the
    * reactive read path in `net.nmoncho.helenus.pekko`, for a bound statement we
    * have already tagged with a routing token.
    */
  private def rangeReadSource[Out](
      bstmt: ScalaBoundStatement[Out]
  )(implicit session: CassandraSession): Source[Out, NotUsed] =
    Source
      .future(session.underlying())
      .flatMapConcat((cql: CqlSession) => Source.fromPublisher(bstmt.executeReactive()(cql)))
}
