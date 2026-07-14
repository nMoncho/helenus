/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import scala.annotation.implicitNotFound
import scala.annotation.unused

import shapeless.HList
import shapeless.HNil

/** Phantom mode of a DELETE builder, tracked at the type level because CQL
  * applies different WHERE rules to each.
  */
sealed trait DeleteMode

object DeleteMode {

  /** Deletes whole rows: a full partition, a clustering slice, or a range. */
  sealed trait Rows extends DeleteMode

  /** Deletes specific columns, which must target exactly one row. */
  sealed trait Columns extends DeleteMode
}

/** Evidence that a DELETE with constraint state (`Eq`, `Rng`) is valid CQL.
  *
  * Row deletes (`M = DeleteMode.Rows`) follow the primary-key restriction
  * rules: full partition key by `===`, contiguous clustering `===` prefix,
  * optionally a trailing range (a range delete). Clustering columns may be
  * omitted entirely (whole-partition delete), but a WHERE clause is
  * mandatory: DELETE without WHERE is not CQL (that is TRUNCATE), so unlike
  * SELECT there is no unrestricted case and no ALLOW FILTERING fallback.
  *
  * Column-level deletes (`M = DeleteMode.Columns`) must pinpoint exactly one
  * row: every primary-key column (partition and clustering) constrained with
  * `===` and no range predicates at all.
  */
@implicitNotFound(
  "This DELETE is not valid CQL and cannot be executed. " +
    "Row deletes require the whole partition key ${PK} to be constrained with ===, " +
    "then optionally a contiguous === prefix of the clustering columns ${CK} " +
    "with at most a trailing range (DELETE has no ALLOW FILTERING and needs a WHERE clause). " +
    "Column-level deletes must target exactly one row: every primary-key column " +
    "constrained with === and no ranges. IN is not supported for DELETE by this DSL. " +
    "Constrained so far: equality on ${Eq}, IN on ${In}, ranges on ${Rng}."
)
sealed trait CanDelete[
    M <: DeleteMode,
    PK <: HList,
    CK <: HList,
    Eq <: HList,
    In <: HList,
    Rng <: HList
]

object CanDelete {

  /** Note the `HNil` IN set in the result: this DSL keeps DELETE IN-free. */
  implicit def rowDelete[PK <: HList, CK <: HList, Eq <: HList, Rng <: HList](
      implicit @unused restriction: PrimaryKeyRestriction[PK, CK, Eq, Rng]
  ): CanDelete[DeleteMode.Rows, PK, CK, Eq, HNil, Rng] =
    new CanDelete[DeleteMode.Rows, PK, CK, Eq, HNil, Rng] {}

  /** Note the `HNil` IN and range sets in the result: both rule this out. */
  implicit def columnDelete[PK <: HList, CK <: HList, Eq <: HList](
      implicit @unused wholeKey: FullPrimaryKey[PK, CK, Eq]
  ): CanDelete[DeleteMode.Columns, PK, CK, Eq, HNil, HNil] =
    new CanDelete[DeleteMode.Columns, PK, CK, Eq, HNil, HNil] {}
}
