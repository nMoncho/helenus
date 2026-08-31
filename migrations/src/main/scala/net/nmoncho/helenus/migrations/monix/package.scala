/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import _root_.monix.reactive.Observable
import _root_.net.nmoncho.helenus.api.cql.ScalaBoundStatement
import _root_.net.nmoncho.helenus.internal.cql.ScalaPreparedStatement2
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.api.core.metadata.token.TokenRange

/** Monix executor for a token-range full-table scan.
  *
  * Turns a [[RingPlan]] into a Monix `Observable`, reading several ranges
  * concurrently so throughput is decoupled from the number of nodes. Each range is
  * bound with the core token codec (see [[TokenRangePlanner]]) and carries a routing
  * token, so the driver sends the query to an owning replica. A range that times out
  * is retried by splitting it into halves (see [[RetryPolicy]]). The upper bound is
  * normalized so the wrap seam is not dropped ([[TokenRing.normalizeUpperBound]]).
  */
package object monix {

  /** A sensible default read concurrency: one in-flight range per available core. */
  final val DefaultParallelism: Int = Runtime.getRuntime.availableProcessors

  implicit class TokenRangeMonixReadOps[Out](
      private val pstmt: ScalaPreparedStatement2[Token, Token, Out]
  ) extends AnyVal {

    /** Reads a whole table by scanning every range in `plan`.
      *
      * The prepared statement must be a bounded token-range query shaped like
      * `SELECT ... WHERE token(pk) > ? AND token(pk) <= ?`. The ranges are split into
      * `parallelism` groups that are read concurrently (each group sequentially), so
      * a single process can saturate the cluster rather than reading one range at a
      * time. Results are emitted as they arrive and are therefore unordered.
      *
      * There is no default rate cap: with `rateLimit` left as `None` the scan runs as
      * fast as the cluster and backpressure allow. Pass a [[RateLimit]] only to
      * deliberately shape the rate.
      *
      * A range that times out is retried by splitting it into halves per `retry`. A
      * retry re-reads the range from the start, so for a range that had already
      * emitted rows the retry may re-emit them; this is safe for an idempotent
      * (upsert) load, which is the migration case.
      *
      * Ranges already recorded in `checkpoint` are skipped, and each range is marked
      * completed once it has been fully read, so a restart resumes rather than
      * re-scanning (see [[Checkpoint]]).
      *
      * Pass `executionProfile` to run the reads under a named driver execution
      * profile, for example a lower read consistency for throughput.
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
    def asTokenRangeObservable(
        plan: RingPlan,
        parallelism: Int                 = DefaultParallelism,
        rateLimit: Option[RateLimit]     = None,
        retry: RetryPolicy               = RetryPolicy.Default,
        checkpoint: Checkpoint           = Checkpoint.None,
        executionProfile: Option[String] = None,
        metrics: MigrationMetrics        = MigrationMetrics.none,
        routing: Boolean                 = true
    )(implicit session: CqlSession): Observable[Out] = {
      val progress = new java.util.concurrent.atomic.AtomicReference(RingProgress(plan))

      val scanned =
        if (plan.isEmpty) {
          Observable.empty[Out]
        } else {
          val p = Math.max(1, parallelism)

          // Round-robin the ranges into `p` groups. Merging the `p` groups reads up to
          // `p` ranges at once, while each group is read sequentially (concat).
          val groups = plan.splits.zipWithIndex
            .groupBy { case (_, index) => index % p }
            .toVector
            .sortBy { case (bucket, _) => bucket }
            .map { case (_, indexed) => indexed.map { case (split, _) => split } }

          val perGroup = groups.map { group =>
            Observable
              .fromIterable(group)
              .flatMap(split =>
                scanSplit(
                  pstmt,
                  split,
                  retry,
                  checkpoint,
                  executionProfile,
                  routing,
                  metrics,
                  progress
                )
              )
          }

          Observable.fromIterable(perGroup).mergeMap(identity)
        }

      val source = scanned
        .map { row => metrics.rowExtracted(); row }
        .onErrorRecoverWith { case error =>
          metrics.rangeFailed(error); Observable.raiseError(error)
        }

      rateLimit.fold(source)(limit => source.throttle(limit.per, limit.elements))
    }

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

  /** Binds a [[TokenRange]] into a routing-aware bound statement.
    *
    * The bounds are bound with the core token codec, the upper bound is normalized so
    * the wrap seam is not dropped ([[TokenRing.normalizeUpperBound]]), and the routing
    * token is set to the range start so the driver routes the query to an owning
    * replica. When `executionProfile` is set, the statement runs under that named
    * driver profile (for example a read-specific consistency).
    */
  private def boundForRange[Out](
      pstmt: ScalaPreparedStatement2[Token, Token, Out],
      range: TokenRange,
      executionProfile: Option[String],
      routing: Boolean
  ): ScalaBoundStatement[Out] = {
    val bound  = pstmt(range.getStart, TokenRing.normalizeUpperBound(range.getEnd))
    val routed = if (routing) bound.setRoutingToken(range.getStart) else bound

    executionProfile.fold(routed)(routed.setExecutionProfileName)
  }

  /** Reads one split: skips it when the checkpoint already has it, otherwise reads it
    * (with timeout retries) and marks it completed once fully read. Completion is
    * appended as an empty observable so the mark runs after the range finishes
    * successfully, and never on failure (`appendAll` does not subscribe to its second
    * observable if the first errors).
    */
  private def scanSplit[Out](
      pstmt: ScalaPreparedStatement2[Token, Token, Out],
      split: RangeSplit,
      retry: RetryPolicy,
      checkpoint: Checkpoint,
      executionProfile: Option[String],
      routing: Boolean,
      metrics: MigrationMetrics,
      progress: java.util.concurrent.atomic.AtomicReference[RingProgress]
  )(implicit session: CqlSession): Observable[Out] =
    if (checkpoint.isCompleted(split)) {
      // Already done on a previous run: advance progress but do not re-read.
      completed[Out](metrics.rangeCompleted(progress.updateAndGet(_.completing(split))))
    } else {
      readWithRetry(split.range, depth = 0, retry)(range =>
        rangeReadObservable(boundForRange(pstmt, range, executionProfile, routing))
      ).appendAll(completed[Out] {
        checkpoint.markCompleted(split)
        metrics.rangeCompleted(progress.updateAndGet(_.completing(split)))
      })
    }

  /** Runs a side effect on subscription and emits nothing, used to record a range
    * completion in-stream without adding elements.
    */
  private def completed[Out](effect: => Unit): Observable[Out] =
    Observable.eval(effect).flatMap(_ => Observable.empty[Out])

  /** Reads a single range, retrying on timeout by splitting it into halves.
    *
    * On a retryable failure ([[RetryPolicy.isRetryable]]) the range is halved and each
    * half is read (recursively) at the next depth. When the range cannot be split
    * further or `depth` reaches `retry.maxSplits`, the failure is surfaced as a
    * [[TokenRangeReadException]] naming the range. `read` is a parameter so the
    * split-and-retry orchestration is unit-testable with an injected failing read.
    */
  private[monix] def readWithRetry[Out](
      range: TokenRange,
      depth: Int,
      retry: RetryPolicy
  )(read: TokenRange => Observable[Out]): Observable[Out] =
    read(range).onErrorHandleWith {
      case ex if RetryPolicy.isRetryable(ex) =>
        val halves = if (depth < retry.maxSplits) TokenRing.halve(range) else Vector.empty

        if (halves.size >= 2) {
          Observable
            .fromIterable(halves)
            .flatMap(half => readWithRetry(half, depth + 1, retry)(read))
        } else {
          Observable.raiseError(new TokenRangeReadException(range, ex))
        }

      case ex =>
        Observable.raiseError(ex)
    }

  /** Reads a single already-bound range statement as an `Observable`, mirroring the
    * reactive read path in `net.nmoncho.helenus.monix`, for a bound statement we have
    * already tagged with a routing token.
    */
  private def rangeReadObservable[Out](
      bstmt: ScalaBoundStatement[Out]
  )(implicit session: CqlSession): Observable[Out] =
    Observable.fromReactivePublisher(bstmt.executeReactive())
}
