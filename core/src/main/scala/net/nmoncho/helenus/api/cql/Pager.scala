/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PagingState
import org.reactivestreams.Publisher

/** Defines the contract for executing queries with pages
  *
  * Each page is defined as an [[Iterator]] which can only be iterated once. Unlike the DSE Java Driver, pages
  * have to be requested explicitly in all cases.
  *
  * @tparam Out result type
  */
trait Pager[Out] {

  /** Whether the query can be executed again for more results
    *
    * @return true if there are more results, false otherwise
    */
  def hasMorePages: Boolean

  /** An optional [[PagingState]] resulting from the previous page
    *
    * @return Some state if this Pager isn't the first nor the final page
    */
  def pagingState: Option[PagingState]

  /** Encodes this page's PagingState in a form that can be serialized
    *
    * @return [[Some]] paging state if there are more pages, [[None]] otherwise
    */
  def encodePagingState(implicit ser: PagerSerializer[_]): Option[ser.SerializedState]

  /** Executes the query associated with this [[Pager]] returning the next pager and current page
    *
    * @param pageSize how many results to include in this page (ie. the [[Iterator]])
    * @return next [[Pager]] and current page
    */
  def execute(pageSize: Int)(implicit session: CqlSession): (Pager[Out], Iterator[Out])

  /** Asynchronously executes the query associated with this [[Pager]] returning the next pager and current page
    *
    * @param pageSize how many results to include in this page (ie. the [[Iterator]])
    * @return next [[Pager]] and current page
    */
  def executeAsync(
      pageSize: Int
  )(implicit session: CqlSession, ec: ExecutionContext): Future[(Pager[Out], Iterator[Out])]

  /** Reactively executes the query associated with this [[Pager]]
    *
    * @param pageSize how many results to include in this page (ie. the [[Iterator]])
    * @return a [[Publisher]] that emits result items with the next [[Pager]]
    */
  def executeReactive(pageSize: Int)(implicit session: CqlSession): Publisher[(Pager[Out], Out)]
}
