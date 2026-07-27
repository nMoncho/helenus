/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables
package dml.where

import scala.annotation.implicitNotFound
import scala.annotation.unused

import shapeless.::
import shapeless.BasisConstraint
import shapeless.HList
import shapeless.HNil
import shapeless.NotContainsConstraint
import shapeless.ops.hlist.Last
import shapeless.ops.hlist.Prepend
import shapeless.ops.hlist.Selector

/** Evidence that a SELECT with the given constraints is executable without
  * ALLOW FILTERING under CQL rules. The qualifying shapes:
  *
  *   1. An unrestricted query: no predicates at all (full table scan), or
  *   2. a primary-key query:
  *      - every partition-key column is constrained with `===`,
  *      - clustering columns are constrained with `===` on a contiguous prefix,
  *        optionally followed by range predicates on the single next
  *        clustering column,
  *      - no non-primary-key column is constrained,
  *   3. either of the above with a single `IN` in place of `===` on the last
  *      partition-key column, or
  *   4. the entire primary key constrained with `===` except an `IN` on the
  *      last clustering column (no ranges in that case).
  *
  * Constraint order is irrelevant: only membership in `Eq` / `In` / `Rng`
  * matters.
  *
  * @tparam PK  the table's partition-key columns (name tags)
  * @tparam CK  the table's clustering columns, in declaration order
  * @tparam Eq  columns constrained with `===` so far
  * @tparam In  columns constrained with `IN` so far
  * @tparam Rng columns constrained with `<`, `>`, `<=`, `>=` so far; also
  *             carries the `RequiresFiltering` marker for predicates that can
  *             never target the primary key (`!==`, `contains`)
  */
@implicitNotFound(
  "This query cannot be executed without ALLOW FILTERING. " +
    "CQL requires the whole partition key ${PK} to be constrained with ===, " +
    "clustering columns ${CK} to be constrained with === on a contiguous prefix " +
    "(optionally followed by range predicates on the next clustering column), " +
    "and no non-primary-key column to be constrained. " +
    "IN is only allowed on the last partition-key column, or on the last " +
    "clustering column (without ranges). " +
    "Constrained so far: equality on ${Eq}, IN on ${In}, ranges on ${Rng}. " +
    "Complete the primary key or use .allowFiltering.execute instead."
)
sealed trait CanSelect[PK <: HList, CK <: HList, Eq <: HList, In <: HList, Rng <: HList]

object CanSelect {

  /** An unrestricted query (no WHERE clause at all) is always executable. */
  implicit def unrestricted[PK <: HList, CK <: HList]: CanSelect[PK, CK, HNil, HNil, HNil] =
    new CanSelect[PK, CK, HNil, HNil, HNil] {}

  /** A query whose predicates form a valid primary-key restriction (no IN). */
  implicit def primaryKeyQuery[PK <: HList, CK <: HList, Eq <: HList, Rng <: HList](
      implicit @unused restriction: PrimaryKeyRestriction[PK, CK, Eq, Rng]
  ): CanSelect[PK, CK, Eq, HNil, Rng] =
    new CanSelect[PK, CK, Eq, HNil, Rng] {}

  /** IN on the last partition-key column: counting the IN column as an
    * equality, the constraints must still form a valid primary-key
    * restriction (so clustering prefixes and trailing ranges stay allowed).
    */
  implicit def partitionIn[
      PK <: HList,
      CK <: HList,
      Eq <: HList,
      In <: HList,
      Rng <: HList,
      L,
      EqIn <: HList
  ](
      implicit @unused lastPartitionColumn: Last.Aux[PK, L],
      @unused inOnLast: AllAre[In, L],
      @unused notAlsoEq: NotContainsConstraint[Eq, L],
      @unused merged: Prepend.Aux[Eq, In, EqIn],
      @unused restriction: PrimaryKeyRestriction[PK, CK, EqIn, Rng]
  ): CanSelect[PK, CK, Eq, In, Rng] =
    new CanSelect[PK, CK, Eq, In, Rng] {}

  /** IN on the last clustering column: every other primary-key column must be
    * `===` constrained and no ranges are allowed.
    */
  implicit def clusteringIn[
      PK <: HList,
      CK <: HList,
      CKCols <: HList,
      Eq <: HList,
      In <: HList,
      L,
      EqIn <: HList
  ](
      implicit @unused stripped: StripOrder.Aux[CK, CKCols],
      @unused lastClusteringColumn: Last.Aux[CKCols, L],
      @unused inOnLast: AllAre[In, L],
      @unused notAlsoEq: NotContainsConstraint[Eq, L],
      @unused merged: Prepend.Aux[Eq, In, EqIn],
      @unused wholeKey: FullPrimaryKey[PK, CK, EqIn]
  ): CanSelect[PK, CK, Eq, In, HNil] =
    new CanSelect[PK, CK, Eq, In, HNil] {}
}

/** Evidence that the constraint state (`Eq`, `Rng`) forms a valid CQL
  * primary-key restriction for a table with partition key `PK` and clustering
  * columns `CK`: the full partition key constrained with `===`, a contiguous
  * clustering `===` prefix, optionally range predicates on the single next
  * clustering column, and nothing else constrained.
  *
  * Shared by the SELECT execute gate ([[CanExecute]]) and the DELETE gate
  * ([[CanDelete]]).
  */
sealed trait PrimaryKeyRestriction[PK <: HList, CK <: HList, Eq <: HList, Rng <: HList]

object PrimaryKeyRestriction {

  implicit def restriction[
      PK <: HList,
      CK <: HList,
      CKCols <: HList,
      Eq <: HList,
      Rng <: HList,
      Keys <: HList
  ](
      implicit @unused stripped: StripOrder.Aux[CK, CKCols],
      @unused keys: Prepend.Aux[PK, CKCols, Keys],
      @unused partitionKeyComplete: BasisConstraint[PK, Eq],
      @unused onlyKeyColumnsConstrained: BasisConstraint[Eq, Keys],
      @unused clusteringShape: ClusteringOk[CKCols, Eq, Rng]
  ): PrimaryKeyRestriction[PK, CK, Eq, Rng] =
    new PrimaryKeyRestriction[PK, CK, Eq, Rng] {}
}

/** Evidence that the equality set `Eq` constrains the entire primary key of a
  * table (every partition-key and clustering column) and nothing else. This
  * is the strictest WHERE shape in CQL: it pinpoints exactly one row. Shared
  * by the UPDATE gate ([[CanUpdate]]) and the column-level DELETE gate
  * ([[CanDelete]]).
  */
sealed trait FullPrimaryKey[PK <: HList, CK <: HList, Eq <: HList]

object FullPrimaryKey {

  implicit def full[PK <: HList, CK <: HList, CKCols <: HList, Keys <: HList, Eq <: HList](
      implicit @unused stripped: StripOrder.Aux[CK, CKCols],
      @unused keys: Prepend.Aux[PK, CKCols, Keys],
      @unused wholeKeyConstrained: BasisConstraint[Keys, Eq],
      @unused onlyKeyColumnsConstrained: BasisConstraint[Eq, Keys]
  ): FullPrimaryKey[PK, CK, Eq] =
    new FullPrimaryKey[PK, CK, Eq] {}
}

/** Type-level map that removes [[Asc]] / [[Desc]] sort markers from a `CK`
  * declaration, leaving the bare column singleton types the gate compares
  * against the constrained-column sets.
  */
sealed trait StripOrder[L <: HList] {
  type Out <: HList
}

object StripOrder {
  type Aux[L <: HList, O <: HList] = StripOrder[L] { type Out = O }

  implicit val hnil: Aux[HNil, HNil] =
    new StripOrder[HNil] { type Out = HNil }

  /** A bare column name tag passes through unchanged. */
  implicit def bareHead[H <: String, T <: HList, TO <: HList](
      implicit @unused tail: Aux[T, TO]
  ): Aux[H :: T, H :: TO] =
    new StripOrder[H :: T] { type Out = H :: TO }

  implicit def ascHead[C, T <: HList, TO <: HList](
      implicit @unused tail: Aux[T, TO]
  ): Aux[Asc[C] :: T, C :: TO] =
    new StripOrder[Asc[C] :: T] { type Out = C :: TO }

  implicit def descHead[C, T <: HList, TO <: HList](
      implicit @unused tail: Aux[T, TO]
  ): Aux[Desc[C] :: T, C :: TO] =
    new StripOrder[Desc[C] :: T] { type Out = C :: TO }
}

/** Walks the clustering columns `CK` in declaration order and witnesses the
  * CQL restriction shape: a contiguous `===`-constrained prefix, optionally
  * followed by range predicates on exactly one column, with every later
  * clustering column left unconstrained.
  */
sealed trait ClusteringOk[CK <: HList, Eq <: HList, Rng <: HList]

object ClusteringOk extends ClusteringOkLowPriority {

  /** All clustering columns consumed by the prefix: no ranges may remain. */
  implicit def exhausted[Eq <: HList]: ClusteringOk[HNil, Eq, HNil] =
    new ClusteringOk[HNil, Eq, HNil] {}

  /** Head is `===`-constrained: keep walking the prefix. */
  implicit def eqHead[H, T <: HList, Eq <: HList, Rng <: HList](
      implicit @unused constrained: Selector[Eq, H],
      @unused rest: ClusteringOk[T, Eq, Rng]
  ): ClusteringOk[H :: T, Eq, Rng] =
    new ClusteringOk[H :: T, Eq, Rng] {}
}

sealed trait ClusteringOkLowPriority {

  /** Head is range-constrained: every range predicate must target it (a slice
    * such as `ts > a AND ts <= b` is fine) and no later clustering column may
    * be constrained.
    */
  implicit def rangeHead[H, T <: HList, Eq <: HList, Rng <: HList](
      implicit @unused notEq: NotContainsConstraint[Eq, H],
      @unused rangesOnHead: AllAre[Rng, H],
      @unused tailUnconstrained: NoneOf[T, Eq]
  ): ClusteringOk[H :: T, Eq, Rng] =
    new ClusteringOk[H :: T, Eq, Rng] {}

  /** Head unconstrained: the prefix stops here; nothing after may be constrained. */
  implicit def unconstrainedTail[H, T <: HList, Eq <: HList](
      implicit @unused notEq: NotContainsConstraint[Eq, H],
      @unused tailUnconstrained: NoneOf[T, Eq]
  ): ClusteringOk[H :: T, Eq, HNil] =
    new ClusteringOk[H :: T, Eq, HNil] {}
}

/** Witnesses that `L` is non-empty and every element is exactly `X`. */
sealed trait AllAre[L <: HList, X]

object AllAre {
  implicit def single[X]: AllAre[X :: HNil, X] =
    new AllAre[X :: HNil, X] {}

  implicit def multiple[X, T <: HList](implicit @unused rest: AllAre[T, X]): AllAre[X :: T, X] =
    new AllAre[X :: T, X] {}
}

/** Witnesses that no element of `L` is present in `S`. */
sealed trait NoneOf[L <: HList, S <: HList]

object NoneOf {
  implicit def empty[S <: HList]: NoneOf[HNil, S] =
    new NoneOf[HNil, S] {}

  implicit def cons[H, T <: HList, S <: HList](
      implicit @unused headAbsent: NotContainsConstraint[S, H],
      @unused tailAbsent: NoneOf[T, S]
  ): NoneOf[H :: T, S] =
    new NoneOf[H :: T, S] {}
}
