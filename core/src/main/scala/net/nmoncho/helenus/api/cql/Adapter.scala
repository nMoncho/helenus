/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import shapeless._
import shapeless.ops.hlist.Prepend
import shapeless.ops.hlist.Tupler

/** Adapts a type `A` into a type `B`.
  *
  * Useful when wanting to adapt a case class instance into a tuple (e.g. during insert)
  *
  * @tparam A original type
  * @tparam B target type
  */
trait Adapter[A, B] extends Serializable {
  def apply(a: A): B
}

object Adapter {

  def apply[A](implicit noOp: NoOpBuilder[A]): Adapter[A, noOp.Out] =
    noOp.adapter

  def builder[A](implicit builder: AdapterBuilder[A]): Builder[A, builder.Repr] =
    builder.builder

  class Builder[A, R <: HList](to: A => R) {

    /** Builds an [[Adapter]] which transforms type `A` into tuple `B`
      *
      * @param tupler transforms representation `R` to tuple `B`
      * @tparam B target tuple type for `Adapter`
      * @return adapter
      */
    def build[B](implicit tupler: Tupler.Aux[R, B]): Adapter[A, B] =
      (a: A) => tupler(to(a))

    /** Adds a computed column for this adapter
      *
      * @param fn how to compute the column from the original type
      * @param p  computed columns are appended to the end
      * @tparam Col computed column type
      * @return a new [[Builder]] with a computed column
      */
    def withComputedColumn[Col](
        fn: A => Col
    )(implicit p: Prepend[R, Col :: HNil]): Builder[A, p.Out] =
      new Builder[A, p.Out](a => p(to(a), fn(a) :: HNil))
  }

  trait NoOpBuilder[A] extends Serializable {
    type Repr <: HList
    type Out

    def adapter: Adapter[A, Out]
  }

  object NoOpBuilder {
    type Aux[A, B, Repr0] = NoOpBuilder[A] { type Repr = Repr0; type Out = B }

    implicit def noOpBuilder[A, B, R <: HList](
        implicit gen: Generic.Aux[A, R],
        tupler: Tupler.Aux[R, B]
    ): Aux[A, B, R] = new NoOpBuilder[A] {
      type Repr = R
      type Out  = B

      override def adapter: Adapter[A, B] = (a: A) => tupler(gen.to(a))
    }
  }

  trait AdapterBuilder[A] {
    type Repr <: HList

    def builder: Builder[A, Repr]
  }

  object AdapterBuilder {
    type Aux[A, Repr0 <: HList] = AdapterBuilder[A] { type Repr = Repr0 }

    implicit def adapterBuilder[A, R <: HList](implicit gen: Generic.Aux[A, R]): Aux[A, R] =
      new AdapterBuilder[A] {
        type Repr = R

        def builder: Builder[A, R] = new Builder[A, R](gen.to)
      }
  }

}
