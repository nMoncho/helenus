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

import java.lang
import java.nio.ByteBuffer
import java.time.Duration
import java.util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core._
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus.AsyncResultSetOps
import net.nmoncho.helenus.ReactiveResultSetOpt
import net.nmoncho.helenus.ResultSetOps
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps
import org.reactivestreams.Publisher

/** This class is meant to wrap a [[BoundStatement]] and carry a [[RowMapper]]
  *
  * For now it's only used when using CQL String Interpolation, as the other query type defines the same but
  * on a `PreparedStatement` level.
  *
  * @param bstmt wrapped statement
  * @param mapper output row mapper
  * @tparam Out query output type
  */
class WrappedBoundStatement[Out](bstmt: BoundStatement)(implicit mapper: RowMapper[Out])
    extends BoundStatement {

  /** Executes this [[BoundStatement]] in synchronous fashion
    *
    * @return [[PagingIterable]] of [[Out]] output values
    */
  def execute()(implicit session: CqlSession): PagingIterable[Out] =
    session.execute(this).as[Out]

  /** Executes this [[BoundStatement]] in a asynchronous fashion
    *
    * @return a future of [[MappedAsyncPagingIterable]]
    */
  def executeAsync()(
      implicit session: CqlSession,
      ec: ExecutionContext
  ): Future[MappedAsyncPagingIterable[Out]] =
    session.executeAsync(this).asScala.map(_.as[Out])

  /** Executes this [[BoundStatement]] in a reactive fashion
    *
    * @return [[Publisher]] of [[Out]] output values
    */
  def executeReactive()(implicit session: CqlSession): Publisher[Out] =
    session.executeReactive(this).as[Out]

  /** Maps the result from this [[BoundStatement]] with a different [[Out2]]
    * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
    * meant to avoid calling `as` twice)
    */
  def as[Out2](implicit newMapper: RowMapper[Out2], ev: Out =:= Row): WrappedBoundStatement[Out2] =
    new WrappedBoundStatement(bstmt)(newMapper)

  // format: off
  override def getPreparedStatement: PreparedStatement = bstmt.getPreparedStatement
  override def getValues: util.List[ByteBuffer] = bstmt.getValues
  override def firstIndexOf(id: CqlIdentifier): Int = bstmt.firstIndexOf(id)
  override def setBytesUnsafe(i: Int, v: ByteBuffer): BoundStatement = bstmt.setBytesUnsafe(i, v)
  override def getBytesUnsafe(i: Int): ByteBuffer = bstmt.getBytesUnsafe(i)
  override def firstIndexOf(name: String): Int = bstmt.firstIndexOf(name)
  override def size(): Int = bstmt.size()
  override def getType(i: Int): DataType = bstmt.getType(i)
  override def setExecutionProfileName(newConfigProfileName: String): BoundStatement = bstmt.setExecutionProfileName(newConfigProfileName)
  override def setExecutionProfile(newProfile: DriverExecutionProfile): BoundStatement = bstmt.setExecutionProfile(newProfile)
  override def setRoutingKeyspace(newRoutingKeyspace: CqlIdentifier): BoundStatement = bstmt.setRoutingKeyspace(newRoutingKeyspace)
  override def setNode(node: Node): BoundStatement = bstmt.setNode(node)
  override def setRoutingKey(newRoutingKey: ByteBuffer): BoundStatement = bstmt.setRoutingKey(newRoutingKey)
  override def setRoutingToken(newRoutingToken: Token): BoundStatement = bstmt.setRoutingToken(newRoutingToken)
  override def setCustomPayload(newCustomPayload: util.Map[String, ByteBuffer]): BoundStatement = bstmt.setCustomPayload(newCustomPayload)
  override def setIdempotent(newIdempotence: lang.Boolean): BoundStatement = bstmt.setIdempotent(newIdempotence)
  override def setTracing(newTracing: Boolean): BoundStatement = bstmt.setTracing(newTracing)
  override def getQueryTimestamp: Long = bstmt.getQueryTimestamp
  override def setQueryTimestamp(newTimestamp: Long): BoundStatement = bstmt.setQueryTimestamp(newTimestamp)
  override def setTimeout(newTimeout: Duration): BoundStatement = bstmt.setTimeout(newTimeout)
  override def getPagingState: ByteBuffer = bstmt.getPagingState
  override def setPagingState(newPagingState: ByteBuffer): BoundStatement = bstmt.setPagingState(newPagingState)
  override def getPageSize: Int = bstmt.getPageSize
  override def setPageSize(newPageSize: Int): BoundStatement = bstmt.setPageSize(newPageSize)
  override def getConsistencyLevel: ConsistencyLevel = bstmt.getConsistencyLevel
  override def setConsistencyLevel(newConsistencyLevel: ConsistencyLevel): BoundStatement = bstmt.setConsistencyLevel(newConsistencyLevel)
  override def getSerialConsistencyLevel: ConsistencyLevel = bstmt.getSerialConsistencyLevel
  override def setSerialConsistencyLevel(newSerialConsistencyLevel: ConsistencyLevel): BoundStatement = bstmt.setSerialConsistencyLevel(newSerialConsistencyLevel)
  override def isTracing: Boolean = bstmt.isTracing
  override def getExecutionProfileName: String = bstmt.getExecutionProfileName
  override def getExecutionProfile: DriverExecutionProfile = bstmt.getExecutionProfile
  override def getRoutingKeyspace: CqlIdentifier = bstmt.getRoutingKeyspace
  override def getRoutingKey: ByteBuffer = bstmt.getRoutingKey
  override def getRoutingToken: Token = bstmt.getRoutingToken
  override def getCustomPayload: util.Map[String, ByteBuffer] = bstmt.getCustomPayload
  override def isIdempotent: lang.Boolean = bstmt.isIdempotent
  override def getTimeout: Duration = bstmt.getTimeout
  override def getNode: Node = bstmt.getNode
  override def codecRegistry(): CodecRegistry = bstmt.codecRegistry()
  override def protocolVersion(): ProtocolVersion = bstmt.protocolVersion()
  // format: on
}
