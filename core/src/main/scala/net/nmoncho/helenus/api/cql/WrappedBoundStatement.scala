/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core._
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatementUnit

/** This class is meant to wrap a [[BoundStatement]] and carry a [[RowMapper]]
  *
  * For now it's only used when using CQL String Interpolation, as the other query type defines the same but
  * on a `PreparedStatement` level.
  *
  * @param bstmt wrapped statement
  * @param mapper output row mapper
  * @tparam Out query output type
  */
class WrappedBoundStatement[Out](pstmt: ScalaPreparedStatementUnit[Out], bstmt: BoundStatement)(
    implicit mapper: RowMapper[Out]
) extends ScalaBoundStatement[Out](pstmt, bstmt) {

  /** Maps the result from this [[BoundStatement]] with a different [[Out2]]
    * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
    * meant to avoid calling `as` twice)
    */
  def as[Out2](implicit newMapper: RowMapper[Out2], ev: Out =:= Row): WrappedBoundStatement[Out2] =
    new WrappedBoundStatement(pstmt.as(newMapper), bstmt)(newMapper)

}

object WrappedBoundStatement {

  def apply(bstmt: BoundStatement): WrappedBoundStatement[Row] = {
    val sp = new ScalaPreparedStatementUnit[Row](
      bstmt.getPreparedStatement,
      RowMapper.identity,
      StatementOptions.default
    )
    new WrappedBoundStatement[Row](sp, bstmt)(RowMapper.identity)
  }

  implicit class FutureWrappedStatementOps[Out](private val fut: Future[WrappedBoundStatement[Out]])
      extends AnyVal {

    /** Maps the result from this [[BoundStatement]] with a different [[Out2]]
      * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
      * meant to avoid calling `as` twice)
      */
    def as[Out2](
        implicit newMapper: RowMapper[Out2],
        ev: Out =:= Row,
        ec: ExecutionContext
    ): Future[WrappedBoundStatement[Out2]] = fut.map(_.as[Out2])

    /** Executes this [[BoundStatement]] in a asynchronous fashion
      *
      * @return a future of [[MappedAsyncPagingIterable]]
      */
    def executeAsync()(
        implicit session: CqlSession,
        ec: ExecutionContext
    ): Future[MappedAsyncPagingIterable[Out]] = fut.flatMap(_.executeAsync())

    def pager(implicit ec: ExecutionContext): Future[Pager[Out]] =
      fut.map(_.pager)

    def pager(pagingState: PagingState)(implicit ec: ExecutionContext): Future[Pager[Out]] =
      fut.map(_.pager(pagingState).get)

    def pager[A: PagerSerializer](pagingState: A)(
        implicit ec: ExecutionContext
    ): Future[Pager[Out]] =
      fut.map(_.pager(pagingState).get)
  }

}
