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

import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter

/** A `ScalaPreparedStatement` that can adapt its [[In]] parameter with an [[Adapter]].
  *
  * This is useful when inserting case classes representing tables.
  *
  * @param pstmt wrapped instance
  * @param mapper how to map results into [[Out]] values
  * @tparam In statement input value
  * @tparam Out statement output value
  */
abstract class ScalaPreparedStatementWithResultAdapter[In, Out](
    pstmt: PreparedStatement,
    mapper: RowMapper[Out]
) extends PreparedStatementWrapper(pstmt) {

  // Since this is no longer exposed to users, we can use the tupled `apply` function
  protected def tupled: In => BoundStatement

  /** Adapts this [[ScalaPreparedStatement]] converting [[In2]] values with the provided adapter
    * into a [[In]] value (ie. the original type of this statement)
    *
    * @param adapter how to adapt an [[In2]] value into [[In]] value
    * @tparam In2 new input type
    * @return adapted [[ScalaPreparedStatement]] with new [[In2]] input type
    */
  def from[In2](implicit adapter: Adapter[In2, In]): ScalaPreparedStatement[In2, Out] =
    new ScalaPreparedStatement[In2, Out](pstmt, mapper) {
      override def apply(t1: In2): BoundStatement = tupled(adapter(t1))
    }
}
