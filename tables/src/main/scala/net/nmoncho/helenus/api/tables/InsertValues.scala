/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import net.nmoncho.helenus.api.ColumnNamingScheme
import shapeless.{ ::, HList, HNil, LabelledGeneric, Witness }
import shapeless.labelled.FieldType

import scala.annotation.implicitNotFound
import scala.collection.mutable

/** Renders every field of a case-class instance `A` as a `(cqlName, cqlLiteral)`
  * pair, in field order. Used by `Table.insertFrom` to write a whole entity.
  */
@implicitNotFound(
  "Cannot render the fields of ${A} for insert. " +
    "${A} must be a case class and every one of its fields needs a CQLType instance."
)
trait InsertValues[A] {
  def values(
      a: A,
      byName: mutable.Map[String, TableDef#Column[_]],
      naming: ColumnNamingScheme
  ): List[TableDef#Assignment[_]]
}

object InsertValues {

  trait ReprValues[R <: HList] {
    def values(
        r: R,
        byName: mutable.Map[String, TableDef#Column[_]],
        naming: ColumnNamingScheme
    ): List[TableDef#Assignment[_]]
  }

  object ReprValues {

    implicit val hnil: ReprValues[HNil] = new ReprValues[HNil] {
      def values(
          r: HNil,
          byName: mutable.Map[String, TableDef#Column[_]],
          naming: ColumnNamingScheme
      ): List[TableDef#Assignment[_]] = Nil
    }

    implicit def hcons[K <: Symbol, H, T <: HList](
        implicit witness: Witness.Aux[K],
        rest: ReprValues[T]
    ): ReprValues[FieldType[K, H] :: T] = new ReprValues[FieldType[K, H] :: T] {
      def values(
          r: FieldType[K, H] :: T,
          byName: mutable.Map[String, TableDef#Column[_]],
          naming: ColumnNamingScheme
      ): List[TableDef#Assignment[_]] =
        byName.get(witness.value.name) match {
          case Some(col) =>
            val column = col.asInstanceOf[TableDef#Column[H]]
            (column := r.head) :: rest.values(r.tail, byName, naming)

          case None =>
            rest.values(r.tail, byName, naming)
        }
    }
  }

  implicit def derive[A, R <: HList](
      implicit gen: LabelledGeneric.Aux[A, R],
      repr: ReprValues[R]
  ): InsertValues[A] = new InsertValues[A] {
    def values(
        a: A,
        byName: mutable.Map[String, TableDef#Column[_]],
        naming: ColumnNamingScheme
    ): List[TableDef#Assignment[_]] =
      repr.values(gen.to(a), byName, naming)
  }
}
