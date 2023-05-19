/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus.internal.cql

import java.nio.ByteBuffer
import java.time.Duration

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PagingState
import net.nmoncho.helenus.api.cql.StatementOptions

/** Adds [[StatementOptions]] to a [[ScalaPreparedStatement]].
  *
  * This allows to set options on a [[com.datastax.oss.driver.api.core.cql.PreparedStatement]] level instead of from a
  * [[BoundStatement]] level, as provided by Datastax's Driver.
  */
trait Options {
  type Self <: ScalaPreparedStatement[_, _]

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

  /** Sets the [[DriverExecutionProfile]] for this statement
    *
    * @return new [[BoundStatement]] instance with execution profile
    */
  def withExecutionProfile(profile: DriverExecutionProfile): Self =
    withOptions(options.copy(profile = Some(profile)))

  /** Sets the <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/statements/per_query_keyspace/#relation-to-the-routing-keyspace">Routing Keyspace</a> for this statement
    *
    * @return new [[BoundStatement]] instance with the routing keyspace
    */
  def withRoutingKeyspace(routingKeyspace: CqlIdentifier): Self =
    withOptions(options.copy(routingKeyspace = Some(routingKeyspace)))

  /** Sets the <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/statements/per_query_keyspace/#relation-to-the-routing-keyspace">Routing Key</a> for this statement
    *
    * For composite Routing Keys, see [[com.datastax.oss.driver.internal.core.util.RoutingKey]]
    *
    * @return new [[BoundStatement]] instance with the routing key
    */
  def withRoutingKey(routingKey: ByteBuffer): Self =
    withOptions(options.copy(routingKey = Some(routingKey)))

  /** Enables/Disables <a href="https://docs.datastax.com/en/developer/java-driver/latest/manual/core/tracing/">tracing</a> on this statement
    *
    * @return new [[BoundStatement]] instance with/out tracing
    */
  def withTracing(enabled: Boolean): Self =
    withOptions(options.copy(tracing = enabled))

  /** Sets the query timeout for this statement
    *
    * @return new [[BoundStatement]] instance with timeout
    */
  def withTimeout(timeout: Duration): Self =
    withOptions(options.copy(timeout = Some(timeout)))

  /** Sets the Paging State for this statement
    *
    * @return new [[BoundStatement]] instance with paging state
    */
  def withPagingState(pagingState: ByteBuffer): Self =
    withOptions(options.copy(pagingState = Some(pagingState)))

  /** Sets the [[PagingState]] for this statement
    *
    * @return new [[BoundStatement]] instance with paging state
    */
  def withPagingState(pagingState: PagingState): Self =
    withPagingState(pagingState.getRawPagingState)

  /** Sets the page size for this statement
    *
    * @return new [[BoundStatement]] instance with page size
    */
  def withPageSize(pageSize: Int): Self =
    withOptions(options.copy(pageSize = pageSize))

  /** Sets the [[ConsistencyLevel]] for this statement
    *
    * @return new [[BoundStatement]] instance with consistency level
    */
  def withConsistencyLevel(consistencyLevel: ConsistencyLevel): Self =
    withOptions(options.copy(consistencyLevel = Some(consistencyLevel)))
}
