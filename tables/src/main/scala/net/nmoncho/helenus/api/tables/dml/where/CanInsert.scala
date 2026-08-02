/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables.dml.where

import scala.annotation.implicitNotFound
import scala.annotation.unused

import shapeless.BasisConstraint
import shapeless.HList
import shapeless.ops.hlist.Prepend

/** Evidence that an INSERT assigning the columns `Assigned` is valid CQL.
  *
  * Unlike a WHERE clause, CQL requires an INSERT to set the ENTIRE primary
  * key: every partition-key column `PK` and every clustering column `CK` must
  * be assigned. Non-key columns are optional, so `Assigned` may contain more
  * than the key (the check is one-directional: the key must be covered, extras
  * are ignored). Constraint order is irrelevant: only membership matters.
  *
  * @tparam PK       the table's partition-key columns (name tags)
  * @tparam CK       the table's clustering columns, in declaration order
  * @tparam Assigned the columns assigned by `value(...)` so far (name tags)
  */
@implicitNotFound(
  "This INSERT does not set the whole primary key and cannot be executed. " +
    "CQL requires an INSERT to assign every column of the partition key ${PK} " +
    "and of the clustering columns ${CK} (non-key columns are optional). " +
    "Assigned so far: ${Assigned}. Add the missing value(...) assignments."
)
sealed trait CanInsert[PK <: HList, CK <: HList, Assigned <: HList]

object CanInsert {

  /** The whole primary key (partition key plus clustering columns, with sort
    * markers stripped) must appear in the assigned-column set.
    */
  implicit def wholeKeyAssigned[
      PK <: HList,
      CK <: HList,
      CKCols <: HList,
      Keys <: HList,
      Assigned <: HList
  ](
      implicit @unused stripped: StripOrder.Aux[CK, CKCols],
      @unused keys: Prepend.Aux[PK, CKCols, Keys],
      @unused wholeKeyAssigned: BasisConstraint[Keys, Assigned]
  ): CanInsert[PK, CK, Assigned] =
    new CanInsert[PK, CK, Assigned] {}
}
