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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
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
