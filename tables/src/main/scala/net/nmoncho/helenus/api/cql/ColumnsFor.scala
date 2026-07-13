/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import scala.annotation.implicitNotFound
import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.ColumnNamingScheme
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.LabelledGeneric
import shapeless.Witness
import shapeless.labelled.FieldType

/** A column definition derived from a case-class field: CQL name + CQL type. */
final case class ColumnDef(name: String, cqlType: String)

/** Derives one column per field of the case class `A`, so a table's schema is
  * complete by construction: DDL and full-row projections come from here, not
  * from the column vals users declare (those are checked references used in
  * queries). Field names are translated to CQL names with the table's
  * [[ColumnNamingScheme]] at use time.
  */
@implicitNotFound(
  "Cannot derive the table schema for ${A}. " +
    "${A} must be a case class and every one of its fields needs a CQLType instance."
)
trait ColumnsFor[A] {
  def columnDefs(naming: ColumnNamingScheme): List[ColumnDef]
}

object ColumnsFor {

  @implicitNotFound(
    "Cannot derive columns for the fields ${R}: every field needs a TypeCodec instance."
  )
  trait ReprColumns[R <: HList] {
    def columnDefs(naming: ColumnNamingScheme): List[ColumnDef]
  }

  object ReprColumns {

    implicit val hnil: ReprColumns[HNil] = new ReprColumns[HNil] {
      def columnDefs(naming: ColumnNamingScheme): List[ColumnDef] = Nil
    }

    implicit def hcons[K <: Symbol, H, T <: HList](
        implicit witness: Witness.Aux[K],
        ct: TypeCodec[H],
        rest: ReprColumns[T]
    ): ReprColumns[FieldType[K, H] :: T] = new ReprColumns[FieldType[K, H] :: T] {
      def columnDefs(naming: ColumnNamingScheme): List[ColumnDef] =
        ColumnDef(naming.map(witness.value.name), ct.getCqlType.asCql(false, false)) :: rest
          .columnDefs(naming)
    }
  }

  implicit def derive[A, R <: HList](
      implicit @unused gen: LabelledGeneric.Aux[A, R],
      repr: ReprColumns[R]
  ): ColumnsFor[A] = new ColumnsFor[A] {
    def columnDefs(naming: ColumnNamingScheme): List[ColumnDef] = repr.columnDefs(naming)
  }
}
