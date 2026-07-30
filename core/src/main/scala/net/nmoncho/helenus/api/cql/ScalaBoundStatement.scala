/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import java.lang
import java.nio.ByteBuffer
import java.time.Duration
import java.util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.datastax.oss.driver.api.core._
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.internal.cql.Pager
import org.reactivestreams.Publisher

class ScalaBoundStatement[Out](pstmt: ScalaPreparedStatement[_, Out], bstmt: BoundStatement)(
    implicit val mapper: RowMapper[Out]
) extends BoundStatement {

  import net.nmoncho.helenus.internal.compat.FutureConverters._

  /** Executes this CQL Statement synchronously
    */
  def execute()(implicit session: CqlSession): PagingIterable[Out] =
    session.execute(bstmt).as[Out]

  /** Executes this CQL Statement asynchronously
    */
  def executeAsync()(
      implicit session: CqlSession,
      ec: ExecutionContext
  ): Future[MappedAsyncPagingIterable[Out]] =
    session.executeAsync(bstmt).asScala.map(_.as[Out])

  /** Returns a [[Publisher]] that, once subscribed to, executes the given query and emits all
    * the results.
    */
  def executeReactive()(implicit session: CqlSession): Publisher[Out] =
    session.executeReactive(bstmt).as[Out]

  /** Creates an initial [[Pager]] for this CQL statement
    */
  def pager: Pager[Out] =
    Pager.initial(this)

  /** Creates a continued [[Pager]] for this CQL statement from a [[PagingState]]
    */
  def pager(pagingState: PagingState): Try[Pager[Out]] =
    Pager.continue(this, pagingState)

  /** Creates a continued [[Pager]] for this CQL statement from a [[PagingState]]
    */
  def pager[A: PagerSerializer](pagingState: A): Try[Pager[Out]] =
    Pager.continueFromEncoded(this, pagingState)

  /** Set options to this [[BoundStatement]] while returning the original type
    */
  def withOptions(
      fn: ScalaBoundStatement[Out] => ScalaBoundStatement[Out]
  ): ScalaBoundStatement[Out] = fn(this)

  /** Set options to this [[BoundStatement]] while returning the original type
    */
  def withOptions(options: StatementOptions): ScalaBoundStatement[Out] = options(this)

  override def getPreparedStatement: ScalaPreparedStatement[_, Out] = pstmt

  override def getValues: util.List[ByteBuffer] = bstmt.getValues

  override def firstIndexOf(id: CqlIdentifier): Int = bstmt.firstIndexOf(id)

  override def firstIndexOf(name: String): Int = bstmt.firstIndexOf(name)

  override def getBytesUnsafe(i: Int): ByteBuffer = bstmt.getBytesUnsafe(i)

  override def getExecutionProfileName: String = bstmt.getExecutionProfileName

  override def getExecutionProfile: DriverExecutionProfile = bstmt.getExecutionProfile

  override def getRoutingKeyspace: CqlIdentifier = bstmt.getRoutingKeyspace

  override def getRoutingKey: ByteBuffer = bstmt.getRoutingKey

  override def getRoutingToken: Token = bstmt.getRoutingToken

  override def getCustomPayload: util.Map[String, ByteBuffer] = bstmt.getCustomPayload

  override def isIdempotent: lang.Boolean = bstmt.isIdempotent

  override def getTimeout: Duration = bstmt.getTimeout

  override def getNode: Node = bstmt.getNode

  override def size(): Int = bstmt.size()

  override def getType(i: Int): DataType = bstmt.getType(i)

  override def codecRegistry(): CodecRegistry = bstmt.codecRegistry()

  override def protocolVersion(): ProtocolVersion = bstmt.protocolVersion()

  override def getQueryTimestamp: Long = bstmt.getQueryTimestamp

  override def getPagingState: ByteBuffer = bstmt.getPagingState

  override def getPageSize: Int = bstmt.getPageSize

  override def getConsistencyLevel: ConsistencyLevel = bstmt.getConsistencyLevel

  override def getSerialConsistencyLevel: ConsistencyLevel = bstmt.getSerialConsistencyLevel

  override def isTracing: Boolean = bstmt.isTracing

  override def setBytesUnsafe(i: Int, v: ByteBuffer): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setBytesUnsafe(i, v))

  override def setExecutionProfileName(newConfigProfileName: String): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setExecutionProfileName(newConfigProfileName))

  override def setExecutionProfile(newProfile: DriverExecutionProfile): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setExecutionProfile(newProfile))

  override def setRoutingKeyspace(newRoutingKeyspace: CqlIdentifier): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setRoutingKeyspace(newRoutingKeyspace))

  override def setNode(node: Node): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setNode(node))

  override def setRoutingKey(newRoutingKey: ByteBuffer): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setRoutingKey(newRoutingKey))

  override def setRoutingToken(newRoutingToken: Token): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setRoutingToken(newRoutingToken))

  override def setCustomPayload(
      newCustomPayload: util.Map[String, ByteBuffer]
  ): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setCustomPayload(newCustomPayload))

  override def setIdempotent(newIdempotence: lang.Boolean): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setIdempotent(newIdempotence))

  override def setTracing(newTracing: Boolean): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setTracing(newTracing))

  override def setQueryTimestamp(newTimestamp: Long): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setQueryTimestamp(newTimestamp))

  override def setTimeout(newTimeout: Duration): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setTimeout(newTimeout))

  override def setPagingState(newPagingState: ByteBuffer): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setPagingState(newPagingState))

  override def setPageSize(newPageSize: Int): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setPageSize(newPageSize))

  override def setConsistencyLevel(other: ConsistencyLevel): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setConsistencyLevel(other))

  override def setSerialConsistencyLevel(other: ConsistencyLevel): ScalaBoundStatement[Out] =
    ScalaBoundStatement[Out](pstmt, bstmt.setSerialConsistencyLevel(other))

}

object ScalaBoundStatement {

  def apply[Out: RowMapper](
      pstmt: ScalaPreparedStatement[_, Out],
      bstmt: BoundStatement
  ): ScalaBoundStatement[Out] =
    new ScalaBoundStatement[Out](pstmt, bstmt)

}
