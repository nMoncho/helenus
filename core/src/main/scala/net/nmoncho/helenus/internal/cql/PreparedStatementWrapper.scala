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
import java.util

import com.datastax.oss.driver.api.core.cql._

/** Extends and wraps a [[PreparedStatement]], delegating all methods to the contained instance
  *
  * This abstraction is used so we can treat `ScalaPreparedStatement`s as [[PreparedStatement]],
  * and as integration point
  *
  * @param pstmt wrapped instance
  */
abstract class PreparedStatementWrapper(pstmt: PreparedStatement) extends PreparedStatement {

  override def getId: ByteBuffer = pstmt.getId

  override def getQuery: String = pstmt.getQuery

  override def getVariableDefinitions: ColumnDefinitions = pstmt.getVariableDefinitions

  override def getPartitionKeyIndices: util.List[Integer] = pstmt.getPartitionKeyIndices

  override def getResultMetadataId: ByteBuffer = pstmt.getResultMetadataId

  override def getResultSetDefinitions: ColumnDefinitions = pstmt.getResultSetDefinitions

  override def setResultMetadata(
      newResultMetadataId: ByteBuffer,
      newResultSetDefinitions: ColumnDefinitions
  ): Unit =
    pstmt.setResultMetadata(newResultMetadataId, newResultSetDefinitions)

  override def bind(values: AnyRef*): BoundStatement =
    pstmt.bind(values: _*)

  override def boundStatementBuilder(values: AnyRef*): BoundStatementBuilder =
    pstmt.boundStatementBuilder(values: _*)
}
