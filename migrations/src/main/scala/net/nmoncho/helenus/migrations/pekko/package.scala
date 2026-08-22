/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import _root_.net.nmoncho.helenus.internal.cql.ScalaPreparedStatement2
import _root_.net.nmoncho.helenus.pekko._
import _root_.org.apache.pekko.NotUsed
import _root_.org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import _root_.org.apache.pekko.stream.scaladsl.Source
import com.datastax.oss.driver.api.core.metadata.token.Token

/** Pekko executor for a token-range full-table scan.
  *
  * Turns a [[RingPlan]] into a Pekko `Source`, reading several ranges
  * concurrently so throughput is decoupled from the number of nodes. It builds on
  * the existing `asReadSource` from `net.nmoncho.helenus.pekko`, so the bounds are
  * bound with the core token codec (see [[TokenRangePlanner]]).
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
      * `SELECT ... WHERE token(pk) > ? AND token(pk) <= ?`. Each [[RangeSplit]] is
      * bound through the existing `asReadSource(start, end)`, with the wrap seam
      * normalized so the top slice of the ring is not dropped (see
      * [[TokenRing.normalizeUpperBound]]).
      *
      * Up to `parallelism` ranges are read concurrently via `flatMapMerge`, so a
      * single process can saturate the cluster rather than reading one range at a
      * time. Results are emitted as they arrive and are therefore unordered.
      *
      * @param plan        the ring scan plan from [[TokenRangePlanner]]
      * @param parallelism how many ranges to read at once (defaults to the number
      *                    of available processors); values below 1 are treated as 1
      */
    def asTokenRangeReadSource(
        plan: RingPlan,
        parallelism: Int = DefaultParallelism
    )(implicit session: CassandraSession): Source[Out, NotUsed] =
      if (plan.isEmpty) {
        Source.empty[Out]
      } else {
        Source(plan.splits)
          .flatMapMerge(
            Math.max(1, parallelism),
            split => pstmt.asReadSource(split.start, TokenRing.normalizeUpperBound(split.end))
          )
      }
  }
}
