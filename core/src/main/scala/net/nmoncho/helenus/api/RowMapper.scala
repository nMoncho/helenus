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
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps
import shapeless.{ ::, <:!<, Generic, HList, HNil, IsTuple, LabelledGeneric, Witness }

/** Maps a [[Row]] into a [[T]]
  *
  * @tparam T type to get from a row
  */
trait RowMapper[T] {

  def apply(row: Row): T

}

trait DerivedRowMapper[T] extends RowMapper[T]

trait CaseClassRowMapperDerivation2 {
  /* Case Class RowMapper derivation */
  implicit def lastCCElement[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): DerivedRowMapper[FieldType[K, H] :: HNil] = new DerivedRowMapper[FieldType[K, H] :: HNil] {
    private val column = columnMapper.map(witness.value.name)

    override def apply(row: Row): FieldType[K, H] :: HNil =
      (witness.value ->> row.get(column, codec)).asInstanceOf[FieldType[K, H]] :: HNil
  }

  implicit def hListCCRowMapper[K <: Symbol, H, T <: HList](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      tailRowMapper: DerivedRowMapper[T],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): DerivedRowMapper[FieldType[K, H] :: T] = new DerivedRowMapper[FieldType[K, H] :: T] {
    private val column = columnMapper.map(witness.value.name)

    override def apply(row: Row): FieldType[K, H] :: T =
      (witness.value ->> row.get(column, codec))
        .asInstanceOf[FieldType[K, H]] :: tailRowMapper(row)
  }

  implicit def genericCCRowMapper[T, R](
      implicit gen: LabelledGeneric.Aux[T, R],
      mapper: DerivedRowMapper[R],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): DerivedRowMapper[T] = { (row: Row) =>
    gen.from(mapper(row))
  }
}

object RowMapper extends CaseClassRowMapperDerivation2 {

  val identity: RowMapper[Row] = (row: Row) => row

  def apply[T](implicit mapper: DerivedRowMapper[T]): RowMapper[T] = mapper

  sealed trait DerivedIdxRowMapper[T] extends DerivedRowMapper[T] {
    def apply(idx: Int, row: Row): T

    override def apply(row: Row): T = throw new RuntimeException("invalid operation")

  }

  /* Tuple RowMapper derivation */
  implicit def lastTupleElement[H](
      implicit codec: TypeCodec[H]
  ): DerivedIdxRowMapper[H :: HNil] = new DerivedIdxRowMapper[H :: HNil] {
    override def apply(idx: Int, row: Row): H :: HNil =
      row.get(idx, codec) :: HNil
  }

  implicit def hListTupleElement[H, T <: HList](
      implicit codec: TypeCodec[H],
      tailMapper: DerivedIdxRowMapper[T]
  ): DerivedIdxRowMapper[H :: T] = new DerivedIdxRowMapper[H :: T] {
    override def apply(idx: Int, row: Row): H :: T =
      row.get(idx, codec) :: tailMapper.apply(idx + 1, row)
  }

  implicit def genericTupleRowMapper[T: IsTuple, R](
      implicit gen: Generic.Aux[T, R],
      mapper: DerivedIdxRowMapper[R]
  ): DerivedRowMapper[T] =
    (row: Row) => gen.from(mapper(0, row))

  /** Derives a [[RowMapper]] from a [[TypeCodec]] when [[T]] isn't a `Product`
    */
  implicit def simpleRowMapper[T](implicit codec: TypeCodec[T], ev: T <:!< Product): RowMapper[T] =
    (row: Row) => row.get(0, codec)
}
