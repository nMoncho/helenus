/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import net.nmoncho.helenus.api.cql.ScalaBoundStatement
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement2

/** Cassandra data migration primitives for Helenus.
  *
  * This module provides the building blocks for a Cassandra to Cassandra ETL
  * migration: extract from a source table, transform records, and load into a
  * target table, at full-table scale without read timeouts.
  *
  * It is organized in two layers:
  *   - a pure layer (the partitioner-agnostic token-range planner, and the
  *     progress and checkpoint models) with no stream-engine dependency, and
  *   - an executor layer, one per backend (Pekko, Akka, Monix, ZIO, Flink),
  *     each in its own subpackage and compiled against a `Provided` backend
  *     dependency, that turns a plan into that backend's native stream.
  */
package object migrations {

  /** A sensible default read concurrency: one in-flight range per available core. */
  final val DefaultParallelism: Int = Runtime.getRuntime.availableProcessors

  /** Binds a [[TokenRange]] into a routing-aware bound statement.
    *
    * The bounds are bound with the core token codec, the upper bound is normalized
    * so the wrap seam is not dropped ([[TokenRing.normalizeUpperBound]]), and the
    * routing token is set to the range start so the driver routes the query to an
    * owning replica (the routing keyspace comes from the prepared statement, hence
    * the session keyspace). When `executionProfile` is set, the statement runs under
    * that named driver profile (for example a read-specific consistency).
    */
  private[migrations] def boundForRange[Out](
      pstmt: ScalaPreparedStatement2[Token, Token, Out],
      range: TokenRange,
      executionProfile: Option[String],
      routing: Boolean
  ): ScalaBoundStatement[Out] = {
    val bound  = pstmt(range.getStart, TokenRing.normalizeUpperBound(range.getEnd))
    val routed = if (routing) bound.setRoutingToken(range.getStart) else bound

    executionProfile.fold(routed)(routed.setExecutionProfileName)
  }

}
