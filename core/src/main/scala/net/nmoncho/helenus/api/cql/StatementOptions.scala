/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import java.nio.ByteBuffer
import java.time.Duration

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import net.nmoncho.helenus.api.cql.StatementOptions.BoundStatementOptions
import net.nmoncho.helenus.api.cql.StatementOptions.PreparedStatementOptions

/** Defines a set of options to apply on a [[ScalaPreparedStatement]]
  */
case class StatementOptions(
    pstmtOptions: PreparedStatementOptions,
    bstmtOptions: BoundStatementOptions
) {

  /** Applies the specified options to the provided [[BoundStatement]]
    * @return [[BoundStatement]] with the applied options
    */
  def apply(bs: BoundStatement): BoundStatement =
    if (bstmtOptions == StatementOptions.default.bstmtOptions) bs
    else {
      val builder = new BoundStatementBuilder(bs)
        .setTracing(bstmtOptions.tracing)
        .setPageSize(bstmtOptions.pageSize)
        .setIdempotence(bstmtOptions.idempotent)

      bstmtOptions.profile.foreach(p => builder.setExecutionProfile(p))
      bstmtOptions.routingKeyspace.foreach(ks => builder.setRoutingKeyspace(ks))
      bstmtOptions.routingKey.foreach(rk => builder.setRoutingKey(rk))
      bstmtOptions.timeout.foreach(t => builder.setTimeout(t))
      bstmtOptions.pagingState.foreach(ps => builder.setPagingState(ps))
      bstmtOptions.consistencyLevel.foreach(cl => builder.setConsistencyLevel(cl))

      builder.build()
    }

  // $COVERAGE-OFF$
  def ignoreNullFields: Boolean = pstmtOptions.ignoreNullFields

  def profile: Option[DriverExecutionProfile] = bstmtOptions.profile

  def routingKeyspace: Option[CqlIdentifier] = bstmtOptions.routingKeyspace

  def routingKey: Option[ByteBuffer] = bstmtOptions.routingKey

  def tracing: Boolean = bstmtOptions.tracing

  def timeout: Option[Duration] = bstmtOptions.timeout

  def pagingState: Option[ByteBuffer] = bstmtOptions.pagingState

  def pageSize: Int = bstmtOptions.pageSize

  def consistencyLevel: Option[ConsistencyLevel] = bstmtOptions.consistencyLevel
  // $COVERAGE-ON$
}

object StatementOptions {

  def apply(
      profile: Option[DriverExecutionProfile],
      routingKeyspace: Option[CqlIdentifier],
      routingKey: Option[ByteBuffer],
      tracing: Boolean,
      timeout: Option[Duration],
      pagingState: Option[ByteBuffer],
      pageSize: Int,
      consistencyLevel: Option[ConsistencyLevel],
      idempotent: Boolean
  ): StatementOptions =
    StatementOptions(
      default.pstmtOptions,
      BoundStatementOptions(
        profile,
        routingKeyspace,
        routingKey,
        tracing,
        timeout,
        pagingState,
        pageSize,
        consistencyLevel,
        idempotent
      )
    )

  case class PreparedStatementOptions(ignoreNullFields: Boolean)

  /** Defines a set of options to apply on a [[BoundStatement]]
    *
    * @param profile          A profile in the driver's configuration.
    * @param routingKeyspace  Sets the keyspace to use for token-aware routing.
    * @param routingKey       Sets the key to use for token-aware routing.
    * @param tracing          Sets tracing for execution.
    * @param timeout          Sets how long to wait for this request to complete.
    * @param pagingState      Sets the paging state to send with the statement
    * @param pageSize         Configures how many rows will be retrieved simultaneously in a single network roundtrip
    * @param consistencyLevel Sets the [[ConsistencyLevel]] to use for this statement.
    */
  case class BoundStatementOptions(
      profile: Option[DriverExecutionProfile],
      routingKeyspace: Option[CqlIdentifier],
      routingKey: Option[ByteBuffer],
      tracing: Boolean,
      timeout: Option[Duration],
      pagingState: Option[ByteBuffer],
      pageSize: Int,
      consistencyLevel: Option[ConsistencyLevel],
      idempotent: Boolean
  )

  /** Default Options, takes all configuration options from the session */
  final val default: StatementOptions = StatementOptions(
    PreparedStatementOptions(
      ignoreNullFields = true
    ),
    BoundStatementOptions(
      profile          = None,
      routingKeyspace  = None,
      routingKey       = None,
      tracing          = false,
      timeout          = None,
      pagingState      = None,
      pageSize         = 0, // 0 or negative uses default value defined in configuration
      consistencyLevel = None,
      idempotent       = false
    )
  )

}
