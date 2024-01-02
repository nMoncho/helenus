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

package net.nmoncho.helenus
package internal.cql

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IteratorHasAsScala

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.internal.core.cql.DefaultPagingState
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement
import net.nmoncho.helenus.internal.reactive.EmptyPublisher
import net.nmoncho.helenus.internal.reactive.TakeOperator
import org.reactivestreams.Publisher

class Pager[Out](
    bstmt: ScalaBoundStatement[Out],
    pagingState: Option[PagingState],
    override val hasMorePages: Boolean
)(implicit rowMapper: RowMapper[Out])
    extends net.nmoncho.helenus.api.cql.Pager[Out] {

  require(
    pagingState.forall(_.matches(bstmt)),
    "Either Query String and/or Bound Parameters don't match PagingState and cannot be reused with current state"
  )

  override def encodePagingState: Option[String] = pagingState.map(_.toString)

  override def execute(
      pageSize: Int
  )(implicit session: CqlSession): (cql.Pager[Out], Iterator[Out]) =
    if (hasMorePages) {
      val execution = forExecution(pageSize).execute()

      // limit how many elements are taken by the iterator
      val page = execution.iterator().asScala.take(pageSize)

      next(Option(execution.getExecutionInfo.getSafePagingState), !execution.isFullyFetched) -> page
    } else {
      empty
    }

  override def executeAsync(
      pageSize: Int
  )(implicit session: CqlSession, ec: ExecutionContext): Future[(cql.Pager[Out], Iterator[Out])] =
    if (hasMorePages) {
      val execution = forExecution(pageSize).executeAsync()

      execution.map { result =>
        val page = result.currentPage().iterator().asScala

        next(Option(result.getExecutionInfo.getSafePagingState), result.hasMorePages) -> page
      }
    } else {
      Future.successful(empty)
    }

  override def executeReactive(
      pageSize: Int
  )(implicit session: CqlSession): Publisher[(cql.Pager[Out], Out)] =
    if (hasMorePages) {
      val original = session.executeReactive(forExecution(pageSize)).as[(cql.Pager[Out], Out)] {
        case row: ReactiveRow =>
          val pagingState = row.getExecutionInfo.getSafePagingState

          next(Option(pagingState), pagingState != null) -> rowMapper.apply(row)
      }

      val takeOp = new TakeOperator[(cql.Pager[Out], Out)](original, pageSize)
      takeOp.publisher
    } else {
      new EmptyPublisher[(cql.Pager[Out], Out)]
    }

  private def forExecution[B >: Out](pageSize: Int): ScalaBoundStatement[B] =
    pagingState
      .fold[BoundStatement](bstmt)(ps => bstmt.setPagingState(ps))
      .setPageSize(pageSize)
      .asInstanceOf[ScalaBoundStatement[B]]

  private def next(pagingState: Option[PagingState], hasMorePages: Boolean): cql.Pager[Out] =
    new Pager[Out](bstmt, pagingState, hasMorePages)

  private def empty: (cql.Pager[Out], Iterator[Out]) = this -> Iterator.empty
}

object Pager {

  def initial[Out](bstmt: ScalaBoundStatement[Out])(implicit mapper: RowMapper[Out]): Pager[Out] =
    new Pager[Out](bstmt, None, true)

  def continue[Out](bstmt: ScalaBoundStatement[Out], pagingState: String)(
      implicit mapper: RowMapper[Out]
  ): Pager[Out] =
    new Pager[Out](bstmt, Some(DefaultPagingState.fromString(pagingState)), true)

}
