/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables.dml.where

import scala.annotation.implicitNotFound
import scala.annotation.unused

import shapeless.HList
import shapeless.HNil
import shapeless.NotContainsConstraint
import shapeless.ops.hlist.Last
import shapeless.ops.hlist.Prepend

/** Evidence that an UPDATE with constraint state (`Eq`, `In`, `Rng`) is valid
  * CQL.
  *
  * UPDATE has the strictest WHERE rules: the clause must identify rows by the
  * entire primary key, every partition-key and clustering column constrained
  * with `===`, except that the LAST component of the primary key may use `IN`
  * to target several rows. No range predicates, no non-key columns, no
  * ALLOW FILTERING fallback, and a WHERE clause is mandatory.
  */
@implicitNotFound(
  "This UPDATE is not valid CQL and cannot be executed. " +
    "CQL requires the WHERE clause of an UPDATE to identify rows by the entire " +
    "primary key: every column of the partition key ${PK} and of the clustering " +
    "columns ${CK} constrained with === (IN is allowed on the last primary-key " +
    "component only), with no range predicates and no non-key columns " +
    "(UPDATE has no ALLOW FILTERING). " +
    "Constrained so far: equality on ${Eq}, IN on ${In}, ranges on ${Rng}."
)
sealed trait CanUpdate[PK <: HList, CK <: HList, Eq <: HList, In <: HList, Rng <: HList]

object CanUpdate {

  /** Note the `HNil` range set in the result: a range predicate rules this out. */
  implicit def fullKey[PK <: HList, CK <: HList, Eq <: HList](
      implicit @unused wholeKey: FullPrimaryKey[PK, CK, Eq]
  ): CanUpdate[PK, CK, Eq, HNil, HNil] =
    new CanUpdate[PK, CK, Eq, HNil, HNil] {}

  /** IN on the last component of the primary key: counting the IN column as
    * an equality, the entire primary key must still be covered.
    */
  implicit def fullKeyWithIn[
      PK <: HList,
      CK <: HList,
      CKCols <: HList,
      Keys <: HList,
      L,
      Eq <: HList,
      In <: HList,
      EqIn <: HList
  ](
      implicit @unused stripped: StripOrder.Aux[CK, CKCols],
      @unused keys: Prepend.Aux[PK, CKCols, Keys],
      @unused lastKeyComponent: Last.Aux[Keys, L],
      @unused inOnLast: AllAre[In, L],
      @unused notAlsoEq: NotContainsConstraint[Eq, L],
      @unused merged: Prepend.Aux[Eq, In, EqIn],
      @unused wholeKey: FullPrimaryKey[PK, CK, EqIn]
  ): CanUpdate[PK, CK, Eq, In, HNil] =
    new CanUpdate[PK, CK, Eq, In, HNil] {}
}
