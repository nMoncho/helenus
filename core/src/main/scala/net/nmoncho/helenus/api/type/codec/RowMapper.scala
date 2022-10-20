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

package net.nmoncho.helenus.api.`type`.codec

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import shapeless.{ ::, <:!<, Generic, HList, HNil, IsTuple }

/** Maps a [[Row]] into a [[T]]
  *
  * @tparam T type to get from a row
  */
trait RowMapper[T] {

  def apply(row: Row): T

}

object RowMapper {

  sealed trait DerivedRowMapper[T] {
    def apply(idx: Int, row: Row): T
  }

  implicit def lastTupleElement[H](implicit codec: TypeCodec[H]): DerivedRowMapper[H :: HNil] =
    new DerivedRowMapper[H :: HNil] {
      override def apply(idx: Int, row: Row): H :: HNil =
        row.get(idx, codec) :: HNil
    }

  implicit def hListTupleElement[H, T <: HList](
      implicit codec: TypeCodec[H],
      tailMapper: DerivedRowMapper[T]
  ): DerivedRowMapper[H :: T] =
    new DerivedRowMapper[H :: T] {
      override def apply(idx: Int, row: Row): H :: T =
        row.get(idx, codec) :: tailMapper.apply(idx + 1, row)
    }

  implicit def genericRowMapper[T: IsTuple, R](
      implicit gen: Generic.Aux[T, R],
      mapper: DerivedRowMapper[R]
  ): RowMapper[T] =
    (row: Row) => gen.from(mapper(0, row))

  /** Derives a [[RowMapper]] from a [[TypeCodec]] when [[T]] isn't a `Product`
    */
  implicit def simpleRowMapper[T](implicit codec: TypeCodec[T], ev: T <:!< Product): RowMapper[T] =
    (row: Row) => row.get(0, codec)
}
