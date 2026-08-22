/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import _root_.net.nmoncho.helenus.api.cql.ScalaBoundStatement
import _root_.net.nmoncho.helenus.internal.cql.ScalaPreparedStatement2
import _root_.org.apache.pekko.NotUsed
import _root_.org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
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

  /** A sensible default read concurrency: one in-flight range per available core. */
  final val DefaultParallelism: Int = Runtime.getRuntime.availableProcessors

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
      * deliberately shape the rate (see B3 in PLAN_MIGRATION.md).
      *
      * A range that times out is retried by splitting it into halves per `retry`.
      * A retry re-reads the range from the start, so for a range that had already
      * emitted rows the retry may re-emit them; this is safe for an idempotent
      * (upsert) load, which is the migration case.
      *
      * @param plan        the ring scan plan from [[TokenRangePlanner]]
      * @param parallelism how many ranges to read at once (defaults to the number
      *                    of available processors); values below 1 are treated as 1
      * @param rateLimit   an optional, opt-in cap on emitted rows; `None` means no cap
      * @param retry       how to retry a range that times out (defaults to halving
      *                    it up to four times)
      */
    def asTokenRangeReadSource(
        plan: RingPlan,
        parallelism: Int             = DefaultParallelism,
        rateLimit: Option[RateLimit] = None,
        retry: RetryPolicy           = RetryPolicy.Default
    )(implicit session: CassandraSession): Source[Out, NotUsed] = {
      val source =
        if (plan.isEmpty) {
          Source.empty[Out]
        } else {
          Source(plan.splits)
            .flatMapMerge(
              Math.max(1, parallelism),
              split =>
                readWithRetry(split.range, depth = 0, retry)(range =>
                  rangeReadSource(boundForRange(pstmt, range))
                )
            )
        }

      rateLimit.fold(source)(limit => source.throttle(limit.elements, limit.per))
    }

    /** Binds one [[RangeSplit]] into a routing-aware bound statement. Exposed for
      * testing and advanced use; see [[boundForRange]] for the details.
      */
    def tokenRangeStatement(split: RangeSplit): ScalaBoundStatement[Out] =
      boundForRange(pstmt, split.range)
  }

  /** Binds a [[TokenRange]] into a routing-aware bound statement.
    *
    * The bounds are bound with the core token codec, the upper bound is normalized
    * so the wrap seam is not dropped ([[TokenRing.normalizeUpperBound]]), and the
    * routing token is set to the range start so the driver routes the query to an
    * owning replica (the routing keyspace comes from the prepared statement, hence
    * the session keyspace).
    */
  private def boundForRange[Out](
      pstmt: ScalaPreparedStatement2[Token, Token, Out],
      range: TokenRange
  ): ScalaBoundStatement[Out] =
    pstmt(range.getStart, TokenRing.normalizeUpperBound(range.getEnd))
      .setRoutingToken(range.getStart)

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
