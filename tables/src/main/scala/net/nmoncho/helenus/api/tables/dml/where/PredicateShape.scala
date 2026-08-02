/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables.dml.where

import shapeless.::
import shapeless.HList
import shapeless.HNil

/** Describes the type-level contribution of a WHERE-clause element of static
  * type `P`:
  *
  *   - an [[EqPredicate]] contributes its column tag to the equality set,
  *     EXCEPT an [[IndexEqPredicate]] (`===` on a column declared with
  *     `Table.index`), which contributes nothing: CQL can satisfy an indexed
  *     equality directly through the index, on any column type, so it is
  *     never required to be part of the primary key,
  *   - an [[InPredicate]] contributes its column tag to the IN set,
  *   - a [[RangePredicate]] contributes its column tag to the range set,
  *   - any other [[Predicate]] (`!==`, `contains` / `containsKey` on a
  *     non-indexed column) contributes the [[RequiresFiltering]] marker,
  *     making the ungated `execute` unavailable, except an [[IndexPredicate]]
  *     (`contains` / `containsKey` on a column declared with `Table.index`),
  *     which contributes nothing: CQL can satisfy it directly through the
  *     index,
  *   - the bind variants (built with the `?` marker) contribute exactly like
  *     their literal counterparts, plus their bound value type in `Params`
  *     (which drives `Select.toFunction`),
  *   - a [[Conjunction]] (built with `and`) contributes everything its
  *     members contribute.
  *
  * `where` and the `and` combinator merge these contributions with shapeless
  * `Prepend`, so predicates can be combined freely while the compile-time key
  * tracking is preserved. `Params` is order-sensitive (left to right, in
  * writing order); the constraint sets are not.
  */
sealed trait PredicateShape[P] {
  type Eq <: HList
  type In <: HList
  type Rng <: HList
  type Params <: HList

  def predicates(p: P): List[Predicate[_, _]]
}

object PredicateShape {

  type Aux[P, E <: HList, I <: HList, R <: HList, Pm <: HList] =
    PredicateShape[P] { type Eq = E; type In = I; type Rng = R; type Params = Pm }

  private def instance[P, E <: HList, I <: HList, R <: HList, Pm <: HList](
      f: P => List[Predicate[_, _]]
  ): Aux[P, E, I, R, Pm] =
    new PredicateShape[P] {
      type Eq     = E
      type In     = I
      type Rng    = R
      type Params = Pm

      def predicates(p: P): List[Predicate[_, _]] = f(p)
    }

  // ---- literal predicates -------------------------------------------------

  implicit def equality[Col, T]: Aux[EqPredicate[Col, T], Col :: HNil, HNil, HNil, HNil] =
    instance(List(_))

  /** A `CONTAINS` predicate on a column with a declared secondary index (see
    * `Table.index`): CQL can satisfy it directly through the index, so unlike
    * the general `filtering` case above it contributes nothing and does not
    * force `allowFiltering`.
    * A `CONTAINS` / `CONTAINS KEY` predicate on a column with a declared
    * secondary index (see `Table.index`): CQL can satisfy it directly through
    * the index, so unlike the general `filtering` case above it contributes
    * nothing and does not force `allowFiltering`.
    */
  implicit def indexedContains[Col, T, V]: Aux[IndexPredicate[Col, T, V], HNil, HNil, HNil, HNil] =
    instance(List(_))

  implicit def indexedEntry[Col, T, K, V]
      : Aux[IndexEntryPredicate[Col, T, K, V], HNil, HNil, HNil, HNil] =
    instance(List(_))

  /** An equality predicate on a column with a declared secondary index (see
    * `Table.index`): CQL can satisfy it directly through the index, on any
    * column type, so unlike the general `equality` case above it contributes
    * nothing and is never required to be part of the primary key.
    */
  implicit def indexedEquality[Col, T]: Aux[IndexEqPredicate[Col, T], HNil, HNil, HNil, HNil] =
    instance(List(_))

  implicit def multiValue[Col, T]: Aux[InPredicate[Col, T], HNil, Col :: HNil, HNil, HNil] =
    instance(List(_))

  implicit def range[Col, T]: Aux[RangePredicate[Col, T], HNil, HNil, Col :: HNil, HNil] =
    instance(List(_))

  implicit def filtering[T, V]: Aux[Predicate[T, V], HNil, HNil, RequiresFiltering :: HNil, HNil] =
    instance(List(_))

  // ---- bind predicates (`?` marker): same gate contribution + a parameter --

  implicit def equalityBind[Col, T]
      : Aux[EqBindPredicate[Col, T], Col :: HNil, HNil, HNil, T :: HNil] =
    instance(List(_))

  /** A `CONTAINS` predicate on a column with a declared secondary index (see
    * `Table.index`): CQL can satisfy it directly through the index, so unlike
    * the general `filtering` case above it contributes nothing and does not
    * force `allowFiltering`.
    * A `CONTAINS` / `CONTAINS KEY` predicate on a column with a declared
    * secondary index (see `Table.index`): CQL can satisfy it directly through
    * the index, so unlike the general `filtering` case above it contributes
    * nothing and does not force `allowFiltering`.
    */
  implicit def indexedBindContains[Col, T, V]
      : Aux[IndexBindPredicate[Col, T, V], HNil, HNil, HNil, V :: HNil] =
    instance(List(_))

  implicit def indexedEntryBind[Col, T, K, V]
      : Aux[IndexEntryBindPredicate[Col, T, K, V], HNil, HNil, HNil, (K, V) :: HNil] =
    instance(List(_))

  implicit def multiValueBind[Col, T]
      : Aux[InBindPredicate[Col, T], HNil, Col :: HNil, HNil, Seq[T] :: HNil] =
    instance(List(_))

  implicit def rangeBind[Col, T]
      : Aux[RangeBindPredicate[Col, T], HNil, HNil, Col :: HNil, T :: HNil] =
    instance(List(_))

  implicit def filteringBind[T, V]
      : Aux[BindPredicate[T, V], HNil, HNil, RequiresFiltering :: HNil, V :: HNil] =
    instance(List(_))

  // ---- conjunctions ---------------------------------------------------------

  implicit def conjunction[E <: HList, I <: HList, R <: HList, Pm <: HList]
      : Aux[Conjunction[E, I, R, Pm], E, I, R, Pm] =
    instance(_.predicates)

}
