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
import com.datastax.oss.driver.api.core.cql.PagingState

/** Adds [[StatementOptions]] to a [[ScalaPreparedStatement]].
  *
  * This allows to set options on a [[com.datastax.oss.driver.api.core.cql.PreparedStatement]] level instead of from a
  * [[BoundStatement]] level, as provided by Datastax's Driver.
  */
trait Options[In, Out] {
  type Self <: ScalaPreparedStatement[In, Out]

  /** [[StatementOptions]] to be applied
    */
  def options: StatementOptions

  /** Updates the [[StatementOptions]] to be applied to the statement.
    *
    * @param options new options
    * @return new instance of a `PreparedStatement` with the provided options
    */
  def withOptions(options: StatementOptions): Self

  /** Applies the [[StatementOptions]] to the provided [[BoundStatement]]
    *
    * @param bs bound statement
    * @return new [[BoundStatement]] instance with options applied
    */
  def applyOptions(bs: BoundStatement): BoundStatement =
    options(bs)

  /** Sets whether the [[BoundStatement]] will ignore `null` bind parameters or not.
    *
    * Ignored bind parameters are <em>not</em> the same as `null`, they are <em>missing</em> values.
    *
    * @return new [[ScalaPreparedStatement]] instance with (not) ignored null fields
    */
  def withIgnoreNullFields(ignore: Boolean): Self =
    withOptions(options.copy(pstmtOptions = options.pstmtOptions.copy(ignoreNullFields = ignore)))

  /** Sets the [[DriverExecutionProfile]] for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with execution profile
    */
  def withExecutionProfile(profile: DriverExecutionProfile): Self =
    withOptions(options.copy(bstmtOptions = options.bstmtOptions.copy(profile = Some(profile))))

  /** Sets the <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/statements/per_query_keyspace/#relation-to-the-routing-keyspace">Routing Keyspace</a> for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with the routing keyspace
    */
  def withRoutingKeyspace(routingKeyspace: CqlIdentifier): Self =
    withOptions(
      options.copy(bstmtOptions =
        options.bstmtOptions.copy(routingKeyspace = Some(routingKeyspace))
      )
    )

  /** Sets the <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/statements/per_query_keyspace/#relation-to-the-routing-keyspace">Routing Key</a> for this statement
    *
    * For composite Routing Keys, see [[com.datastax.oss.driver.internal.core.util.RoutingKey]]
    *
    * @return new [[ScalaPreparedStatement]] instance with the routing key
    */
  def withRoutingKey(routingKey: ByteBuffer): Self =
    withOptions(
      options.copy(bstmtOptions = options.bstmtOptions.copy(routingKey = Some(routingKey)))
    )

  /** Enables/Disables <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/tracing/">tracing</a> on this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with/out tracing
    */
  def withTracing(enabled: Boolean): Self =
    withOptions(options.copy(bstmtOptions = options.bstmtOptions.copy(tracing = enabled)))

  /** Sets the query timeout for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with timeout
    */
  def withTimeout(timeout: Duration): Self =
    withOptions(options.copy(bstmtOptions = options.bstmtOptions.copy(timeout = Some(timeout))))

  /** Sets the Paging State for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with paging state
    */
  def withPagingState(pagingState: ByteBuffer): Self =
    withOptions(
      options.copy(bstmtOptions = options.bstmtOptions.copy(pagingState = Some(pagingState)))
    )

  /** Sets the [[PagingState]] for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with paging state
    */
  def withPagingState(pagingState: PagingState): Self =
    withPagingState(pagingState.getRawPagingState)

  /** Sets the page size for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with page size
    */
  def withPageSize(pageSize: Int): Self =
    withOptions(options.copy(bstmtOptions = options.bstmtOptions.copy(pageSize = pageSize)))

  /** Sets the [[ConsistencyLevel]] for this statement
    *
    * @return new [[ScalaPreparedStatement]] instance with consistency level
    */
  def withConsistencyLevel(consistencyLevel: ConsistencyLevel): Self =
    withOptions(
      options.copy(bstmtOptions =
        options.bstmtOptions.copy(consistencyLevel = Some(consistencyLevel))
      )
    )
}
