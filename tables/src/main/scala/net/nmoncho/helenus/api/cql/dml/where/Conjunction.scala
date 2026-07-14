/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import scala.annotation.unused

import shapeless.HList
import shapeless.ops.hlist.Prepend

/** A conjunction of predicates built with `and` inside a `where(...)`, e.g.
  * `where(UsersTable.id === x and UsersTable.username === "alice")`.
  *
  * @tparam E  the merged equality contributions of all members
  * @tparam I  the merged IN contributions of all members
  * @tparam R  the merged range / filtering contributions of all members
  *
  * Carrying the merged type-level contributions is what keeps the execute
  * gates and `toFunction` working across combined predicates: `where` reads
  * them back through the [[PredicateShape]] instance for `Conjunction`.
  */
final class Conjunction[E <: HList, I <: HList, R <: HList](val predicates: List[Predicate])
    extends WhereClause {
  override def toString: String = predicates.map(_.toCQL).mkString(" AND ")
}

object Conjunction {

  /** Extends an existing conjunction: `p1 and p2 and p3` (left associative). */
  implicit final class ConjunctionAndOps[E <: HList, I <: HList, R <: HList](
      private val self: Conjunction[E, I, R]
  ) extends AnyVal {
    def and[P, E2 <: HList, I2 <: HList, R2 <: HList, EO <: HList, IO <: HList, RO <: HList](
        other: P
    )(
        implicit ps: PredicateShape.Aux[P, E2, I2, R2],
        @unused pe: Prepend.Aux[E, E2, EO],
        @unused pi: Prepend.Aux[I, I2, IO],
        @unused pr: Prepend.Aux[R, R2, RO]
    ): Conjunction[EO, IO, RO] =
      new Conjunction(self.predicates ++ ps.predicates(other))
  }
}
