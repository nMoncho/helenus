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

import scala.util.Try

import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.api.core.cql.Statement
import com.datastax.oss.driver.api.core.session.Session
import com.datastax.oss.driver.internal.core.cql.DefaultPagingState
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement

/** Knows how to serialize a [[PagingState]]
  *
  * @tparam In Serialized [[PagingState]] Type
  */
trait PagerSerializer[In] {

  // Dependent-type used also as output
  type SerializedState = In

  /** Tries to serialize a [[PagingState]]
    *
    * @param pagingState to serialize
    * @return Success if successfully serialized, Failure otherwise
    */
  def serialize(pagingState: PagingState): Try[SerializedState]

  /** Tries to deserialize a [[PagingState]] from a serialized form
    *
    * @param bstmt BoundStatement associated with the expected [[PagingState]]
    * @param value serialized form
    * @return Success if successfully deserialized, Failure otherwise
    */
  def deserialize(bstmt: ScalaBoundStatement[_], value: In): Try[PagingState]
}

object PagerSerializer {

  /** Implements [[PagerSerializer]] using [[DefaultPagingState]] serialization
    */
  object DefaultPagingStateSerializer extends PagerSerializer[String] {

    override def serialize(
        pagingState: PagingState
    ): Try[DefaultPagingStateSerializer.SerializedState] =
      Try(pagingState.toString)

    override def deserialize(bstmt: ScalaBoundStatement[_], value: String): Try[PagingState] =
      Try(DefaultPagingState.fromString(value))
  }

  /** Implements [[PagerSerializer]] using the [[PagingState]] raw representation as a [[ByteBuffer]]
    */
  object SimplePagingStateSerializer extends PagerSerializer[ByteBuffer] {

    override def serialize(
        pagingState: PagingState
    ): Try[SimplePagingStateSerializer.SerializedState] =
      Try(pagingState.getRawPagingState)

    override def deserialize(bstmt: ScalaBoundStatement[_], value: ByteBuffer): Try[PagingState] =
      Try(new PagingState {
        override def toBytes: Array[Byte] = value.array()

        // always returns 'true' since the 'unsafe' ByteBuffer is used
        override def matches(statement: Statement[_], session: Session): Boolean = true

        override def getRawPagingState: ByteBuffer = value
      })
  }
}
