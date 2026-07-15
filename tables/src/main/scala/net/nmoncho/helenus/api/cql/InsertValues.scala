/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import scala.annotation.implicitNotFound

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.ColumnNamingScheme
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.LabelledGeneric
import shapeless.Witness
import shapeless.labelled.FieldType

/** Renders every field of a case-class instance `A` as a `(cqlName, cqlLiteral)`
  * pair, in field order. Used by `Table.insertFrom` to write a whole entity.
  */
@implicitNotFound(
  "Cannot render the fields of ${A} for insert. " +
    "${A} must be a case class and every one of its fields needs a CQLType instance."
)
trait InsertValues[A] {
  def values(a: A, naming: ColumnNamingScheme): List[(String, String)]
}

object InsertValues {

  trait ReprValues[R <: HList] {
    def values(r: R, naming: ColumnNamingScheme): List[(String, String)]
  }

  object ReprValues {

    implicit val hnil: ReprValues[HNil] = new ReprValues[HNil] {
      def values(r: HNil, naming: ColumnNamingScheme): List[(String, String)] = Nil
    }

    implicit def hcons[K <: Symbol, H, T <: HList](
        implicit witness: Witness.Aux[K],
        ct: TypeCodec[H],
        rest: ReprValues[T]
    ): ReprValues[FieldType[K, H] :: T] = new ReprValues[FieldType[K, H] :: T] {
      def values(r: FieldType[K, H] :: T, naming: ColumnNamingScheme): List[(String, String)] =
        (naming.apply(witness.value.name), ct.format(r.head)) :: rest.values(r.tail, naming)
    }
  }

  implicit def derive[A, R <: HList](
      implicit gen: LabelledGeneric.Aux[A, R],
      repr: ReprValues[R]
  ): InsertValues[A] = new InsertValues[A] {
    def values(a: A, naming: ColumnNamingScheme): List[(String, String)] =
      repr.values(gen.to(a), naming)
  }
}
