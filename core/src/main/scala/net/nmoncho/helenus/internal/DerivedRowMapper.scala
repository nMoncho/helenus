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

package net.nmoncho.helenus.internal

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import shapeless.::
import shapeless.Generic
import shapeless.HList
import shapeless.HNil
import shapeless.IsTuple
import shapeless.LabelledGeneric
import shapeless.Witness
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps

abstract class DerivedRowMapper[T] extends RowMapper[T]

trait CaseClassRowMapperDerivation {

  sealed trait DerivedNameRowMapper[T] extends DerivedRowMapper[T] {
    def column: String
  }

  /* Case Class RowMapper derivation */
  implicit def lastCCElement[K <: Symbol, H](
      implicit colDecoder: ColumnMapper[H],
      witness: Witness.Aux[K],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): DerivedRowMapper.Builder[FieldType[K, H] :: HNil] =
    (mapping: DerivedRowMapper.FieldToColumn) =>
      new DerivedNameRowMapper[FieldType[K, H] :: HNil] {
        override val column: String =
          mapping.getOrElse(witness.value.name, columnMapper.map(witness.value.name))

        override def apply(row: Row): FieldType[K, H] :: HNil =
          (witness.value ->> colDecoder(column, row)).asInstanceOf[FieldType[K, H]] :: HNil
      }

  implicit def hListCCRowMapper[K <: Symbol, H, T <: HList](
      implicit colDecoder: ColumnMapper[H],
      witness: Witness.Aux[K],
      tailRowBuilder: DerivedRowMapper.Builder[T],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): DerivedRowMapper.Builder[FieldType[K, H] :: T] = (mapping: DerivedRowMapper.FieldToColumn) =>
    new DerivedNameRowMapper[FieldType[K, H] :: T] {
      override val column: String =
        mapping.getOrElse(witness.value.name, columnMapper.map(witness.value.name))

      private val tailRowMapper = tailRowBuilder(mapping)

      override def apply(row: Row): FieldType[K, H] :: T =
        (witness.value ->> colDecoder(column, row))
          .asInstanceOf[FieldType[K, H]] :: tailRowMapper(row)
    }

  /** Derives a [[Builder]] for type [[T]].
    *
    * Which is used when users want to refine field/column mapping.
    * It's also used on the default case (ie. without refined mapping). See [[genericCCRowMapper]].
    */
  implicit def genericCCRowMapperBuilder[T, R](
      implicit gen: LabelledGeneric.Aux[T, R],
      builder: DerivedRowMapper.Builder[R],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): DerivedRowMapper.Builder[T] = (mappings: DerivedRowMapper.FieldToColumn) => {
    val reprRowMapper = builder(mappings)

    (row: Row) => gen.from(reprRowMapper(row))
  }

  /** Derives a [[DerivedRowMapper]] for type [[T]] when refined field/column mapping is not required.
    */
  implicit def genericCCRowMapper[T, R](
      implicit gen: LabelledGeneric.Aux[T, R],
      builder: DerivedRowMapper.Builder[T],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): DerivedRowMapper[T] = builder(Map.empty)

}

object DerivedRowMapper extends CaseClassRowMapperDerivation {

  type FieldToColumn = Map[String, String]

  trait Builder[A] extends (Map[String, String] => DerivedRowMapper[A])

  sealed trait DerivedIdxRowMapper[T] extends DerivedRowMapper[T] {
    def apply(idx: Int, row: Row): T

    // $COVERAGE-OFF$
    override def apply(row: Row): T = throw new UnsupportedOperationException(
      "Invalid operation. DerivedIdxRowMapper requires an index to operate."
    )
    // $COVERAGE-ON$
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

  implicit def genericTupleRowMapper[T, R](
      implicit isTuple: IsTuple[T],
      gen: Generic.Aux[T, R],
      mapper: DerivedIdxRowMapper[R]
  ): DerivedRowMapper[T] =
    (row: Row) => gen.from(mapper(0, row))

}
