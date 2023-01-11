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

package net.nmoncho.helenus.api

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.internal.DerivedRowMapper
import net.nmoncho.helenus.internal.macros.{ RowMapper => RowMapperMacros }
import shapeless.<:!<
import shapeless.IsTuple

/** Maps a [[Row]] into a [[T]]
  *
  * @tparam T type to get from a row
  */
trait RowMapper[T] {

  def apply(row: Row): T

}

object RowMapper {
  import scala.language.experimental.macros

  type ColumnName = String

  val identity: RowMapper[Row] = (row: Row) => row

  /** Knows how to extract a column from a [[Row]] into a Scala type [[A]]
    * @tparam A target type
    */
  trait ColumnMapper[A] {
    def apply(columnName: String, row: Row): A
  }

  object ColumnMapper {
    def default[A](implicit codec: TypeCodec[A]): ColumnMapper[A] = new ColumnMapper[A] {
      override def apply(columnName: String, row: Row): A =
        row.get(columnName, codec)
    }
  }

  def apply[T](implicit mapper: DerivedRowMapper[T]): RowMapper[T] = mapper

  /** Derives a [[RowMapper]] considering the specified name mapping.
    *
    * @param first first mapping from field to column
    * @param rest  rest mapping from field to column
    * @tparam T target type
    */
  def renamed[T](first: T => (Any, ColumnName), rest: T => (Any, ColumnName)*): RowMapper[T] =
    macro RowMapperMacros.renamedMapper[DerivedRowMapper.Builder, T]

  /** Derives a [[RowMapper]] for tuples
    */
  implicit def derivedTupleRowMapper[T: IsTuple](
      implicit mapper: DerivedRowMapper[T]
  ): RowMapper[T] = mapper

  /** Derives a [[RowMapper]] from a [[TypeCodec]] when [[T]] isn't a `Product`
    */
  implicit def simpleRowMapper[T](
      implicit ev: T <:!< Product,
      codec: TypeCodec[T]
  ): DerivedRowMapper[T] =
    (row: Row) => row.get(0, codec)

}
