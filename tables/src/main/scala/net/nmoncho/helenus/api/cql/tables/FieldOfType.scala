/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables

import scala.annotation.implicitNotFound
import scala.annotation.unused

import shapeless.HList
import shapeless.LabelledGeneric
import shapeless.ops.record.Selector
import shapeless.tag.@@

/** Evidence that the case class `A` has a field named `Name` whose type is
  * exactly `V`.
  *
  * Used by `Table.column[V]("name")` as a pure CHECK: `V` is supplied
  * explicitly at the call site (never inferred through this implicit), so an
  * IDE that fails to resolve the shapeless lookup still types the column val
  * correctly and nothing cascades; the compiler enforces that the field
  * exists and that its type matches `V`.
  */
@implicitNotFound(
  "${A} has no field named ${Name} of type ${V}. " +
    "column[V](\"name\") must reference a field of the table's case class, " +
    "with V matching the field's type exactly."
)
sealed trait FieldOfType[A, Name, V]

object FieldOfType {

  implicit def check[A, R <: HList, Name <: String, V](
      implicit @unused gen: LabelledGeneric.Aux[A, R],
      @unused sel: Selector.Aux[R, Symbol @@ Name, V]
  ): FieldOfType[A, Name, V] = new FieldOfType[A, Name, V] {}
}
