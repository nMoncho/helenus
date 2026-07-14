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

  def predicates(p: P): List[Predicate]
}

object PredicateShape {

  type Aux[P, E <: HList, I <: HList, R <: HList] =
    PredicateShape[P] { type Eq = E; type In = I; type Rng = R }

  private def instance[P, E <: HList, I <: HList, R <: HList](
      f: P => List[Predicate]
  ): Aux[P, E, I, R] =
    new PredicateShape[P] {
      type Eq  = E
      type In  = I
      type Rng = R

      def predicates(p: P): List[Predicate] = f(p)
    }

  // ---- literal predicates -------------------------------------------------

  implicit def equality[Col]: Aux[EqPredicate[Col], Col :: HNil, HNil, HNil] =
    instance(List(_))

  implicit def multiValue[Col]: Aux[InPredicate[Col], HNil, Col :: HNil, HNil] =
    instance(List(_))

  implicit def range[Col]: Aux[RangePredicate[Col], HNil, HNil, Col :: HNil] =
    instance(List(_))

  implicit val filtering: Aux[Predicate, HNil, HNil, RequiresFiltering :: HNil] =
    instance(List(_))
}
