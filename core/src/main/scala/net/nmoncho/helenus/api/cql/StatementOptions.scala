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

package net.nmoncho.helenus.api.cql

import java.nio.ByteBuffer
import java.time.Duration

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql.BoundStatement

/** Defines a set of options to apply on a [[BoundStatement]]
  *
  * @param profile A profile in the driver's configuration.
  * @param routingKeyspace Sets the keyspace to use for token-aware routing.
  * @param routingKey Sets the key to use for token-aware routing.
  * @param tracing Sets tracing for execution.
  * @param timeout Sets how long to wait for this request to complete.
  * @param pagingState Sets the paging state to send with the statement
  * @param pageSize Configures how many rows will be retrieved simultaneously in a single network roundtrip
  * @param consistencyLevel Sets the [[ConsistencyLevel]] to use for this statement.
  */
case class StatementOptions(
    profile: Option[DriverExecutionProfile],
    routingKeyspace: Option[CqlIdentifier],
    routingKey: Option[ByteBuffer],
    tracing: Boolean,
    timeout: Option[Duration],
    pagingState: Option[ByteBuffer],
    pageSize: Int,
    consistencyLevel: Option[ConsistencyLevel]
) {

  /** Applies the specified options to the provided [[BoundStatement]]
    * @return [[BoundStatement]] with the applied options
    */
  def apply(bs: BoundStatement): BoundStatement =
    if (this == StatementOptions.default) bs
    else {
      // TODO maybe we can avoid so many allocations with a simple `new`, although it would be less flexible
      val bs1 = bs.setTracing(tracing).setPageSize(pageSize)
      val bs2 = profile.map(bs1.setExecutionProfile).getOrElse(bs1)
      val bs3 = routingKeyspace.map(bs2.setRoutingKeyspace).getOrElse(bs2)
      val bs4 = routingKey.map(bs3.setRoutingKey).getOrElse(bs3)
      val bs5 = timeout.map(bs4.setTimeout).getOrElse(bs4)
      val bs6 = pagingState.map(bs5.setPagingState).getOrElse(bs5)

      consistencyLevel.map(bs6.setConsistencyLevel).getOrElse(bs6)
    }

}

object StatementOptions {

  /** Default Options, takes all configuration options from the session */
  final val default: StatementOptions = StatementOptions(
    profile          = None,
    routingKeyspace  = None,
    routingKey       = None,
    tracing          = false,
    timeout          = None,
    pagingState      = None,
    pageSize         = 0, // 0 or negative uses default value defined in configuration
    consistencyLevel = None
  )

}
