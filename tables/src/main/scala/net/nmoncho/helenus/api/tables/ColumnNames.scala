/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import scala.annotation.implicitNotFound

import shapeless.::
import shapeless.HList
import shapeless.HNil

/** Materializes the runtime column names from a type-level list of column
  * name tags such as `id.Tag :: username.Tag :: HNil` (each tag dealiases to
  * the literal type of the column's name, e.g. `"id"`).
  *
  * Relies on the compiler-provided `ValueOf` instance for literal types, so
  * the names in a table's `PK` declaration are recovered as values with no
  * duplication and no possibility of drift.
  */
@implicitNotFound(
  "Could not resolve the key column names for ${L}. Every element of a table's `type PK` (and " +
    "`type CK`) must be a column's `Tag`, i.e. a concrete singleton of the column name such as " +
    "`id.Tag`. The usual cause is ascribing a column val with an explicit type, " +
    "`val id: Column[X] = column(\"id\")`, which widens away the `Tag` refinement and leaves it " +
    "abstract. Remove the type annotation (write `val id = column[X](\"id\")`) so the column keeps " +
    "its singleton `Tag`."
)
trait ColumnNames[L <: HList] {
  def names: List[String]
}

object ColumnNames {

  implicit val hnil: ColumnNames[HNil] = new ColumnNames[HNil] {
    def names: List[String] = Nil
  }

  implicit def hcons[H <: String with Singleton, T <: HList](
      implicit head: ValueOf[H],
      tail: ColumnNames[T]
  ): ColumnNames[H :: T] = new ColumnNames[H :: T] {
    def names: List[String] = head.value :: tail.names
  }
}
