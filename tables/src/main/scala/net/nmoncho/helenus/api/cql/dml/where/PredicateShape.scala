/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import shapeless.::
import shapeless.HList
import shapeless.HNil

/** Describes the type-level contribution of a WHERE-clause element of static
  * type `P`:
  *
  *   - an [[EqPredicate]] contributes its column tag to the equality set,
  *   - an [[InPredicate]] contributes its column tag to the IN set,
  *   - a [[RangePredicate]] contributes its column tag to the range set,
  *   - any other [[Predicate]] (`!==`, `contains`) contributes the
  *     [[RequiresFiltering]] marker, making the ungated `execute` unavailable,
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

  implicit def multiValue[Col, T, V <: Iterable[T]]
      : Aux[InPredicate[Col, T, V], HNil, Col :: HNil, HNil, HNil] =
    instance(List(_))

  implicit def range[Col, T]: Aux[RangePredicate[Col, T], HNil, HNil, Col :: HNil, HNil] =
    instance(List(_))

  implicit val filtering: Aux[Predicate[_, _], HNil, HNil, RequiresFiltering :: HNil, HNil] =
    instance(List(_))

  // ---- bind predicates (`?` marker): same gate contribution + a parameter --

  implicit def equalityBind[Col, T]
      : Aux[EqBindPredicate[Col, T], Col :: HNil, HNil, HNil, T :: HNil] =
    instance(List(_))

  implicit def multiValueBind[Col, T, V <: Iterable[T]]
      : Aux[InBindPredicate[Col, T, V], HNil, Col :: HNil, HNil, Seq[T] :: HNil] =
    instance(List(_))

  implicit def rangeBind[Col, T]
      : Aux[RangeBindPredicate[Col, T], HNil, HNil, Col :: HNil, T :: HNil] =
    instance(List(_))

  implicit def filteringBind[T, V]
      : Aux[BindPredicate[T, V], HNil, HNil, RequiresFiltering :: HNil, T :: HNil] =
    instance(List(_))

  // ---- conjunctions ---------------------------------------------------------

  implicit def conjunction[E <: HList, I <: HList, R <: HList, Pm <: HList]
      : Aux[Conjunction[E, I, R, Pm], E, I, R, Pm] =
    instance(_.predicates)

}
