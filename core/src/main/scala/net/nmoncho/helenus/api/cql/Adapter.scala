/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
trait Adapter[A, B] {
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

  trait NoOpBuilder[A] {
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
