/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.internal.cql._
import shapeless.::
import shapeless.HList
import shapeless.HNil

/** Dispatches a [[Select]]'s `Params` `HList` (the bound `?` marker types, in
  * writing order) to the matching arity-specific `helenus` constructor —
  * `CQLQuery#prepareUnit` for no parameters, `CQLQuery#prepare[T1]` for one,
  * `CQLQuery#prepare[T1, T2]` for two, and so on through 22 (the same ceiling
  * `helenus` itself supports). There is no generic, arity-agnostic entry
  * point on the `helenus` side to delegate to instead — its
  * `ScalaPreparedStatementN` classes are hand-numbered, not `HList`-driven —
  * so this typeclass exists purely to bridge our `HList` to that numbering,
  * one implicit per arity, each requiring a driver [[TypeCodec]] per bound
  * parameter (`helenus` binds by position, not by our [[com.example.cql.CQLType]]).
  */
sealed trait ToPrepared[Params <: HList] {
  type Out <: ScalaPreparedStatement[_, Row]
  def apply(query: CQLQuery): Out
}

object ToPrepared {
  type Aux[Params <: HList, Out0] = ToPrepared[Params] { type Out = Out0 }

  implicit val arity0: Aux[HNil, ScalaPreparedStatementUnit[Row]] =
    new ToPrepared[HNil] {
      type Out = ScalaPreparedStatementUnit[Row]
      def apply(query: CQLQuery): Out = query.prepareUnit
    }

  implicit def arity1[T1: TypeCodec]: Aux[T1 :: HNil, ScalaPreparedStatement1[T1, Row]] =
    new ToPrepared[T1 :: HNil] {
      type Out = ScalaPreparedStatement1[T1, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1]
    }

  // format: off
  implicit def arity2[T1: TypeCodec, T2: TypeCodec]: Aux[T1 :: T2 :: HNil, ScalaPreparedStatement2[T1, T2, Row]] =
    new ToPrepared[T1 :: T2 :: HNil] {
      type Out = ScalaPreparedStatement2[T1, T2, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2]
    }

  implicit def arity3[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec]: Aux[T1 :: T2 :: T3 :: HNil, ScalaPreparedStatement3[T1, T2, T3, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: HNil] {
      type Out = ScalaPreparedStatement3[T1, T2, T3, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3]
    }

  implicit def arity4[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: HNil, ScalaPreparedStatement4[T1, T2, T3, T4, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: HNil] {
      type Out = ScalaPreparedStatement4[T1, T2, T3, T4, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4]
    }

  implicit def arity5[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: HNil, ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: HNil] {
      type Out = ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5]
    }

  implicit def arity6[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil, ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil] {
      type Out = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6]
    }

  implicit def arity7[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil, ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil] {
      type Out = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7]
    }

  implicit def arity9[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil, ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil] {
      type Out = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8]
    }

  implicit def arity9[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil, ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil] {
      type Out = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9]
    }

  implicit def arity10[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil, ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil] {
      type Out = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]
    }

  implicit def arity11[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil, ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil] {
      type Out = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]
    }

  implicit def arity12[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil, ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil] {
      type Out = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]
    }

  implicit def arity13[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil, ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil] {
      type Out = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]
    }

  implicit def arity14[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil, ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil] {
      type Out = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]
    }

  implicit def arity15[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil, ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil] {
      type Out = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]
    }

  implicit def arity16[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil, ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil] {
      type Out = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]
    }

  implicit def arity17[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil, ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil] {
      type Out = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]
    }

  implicit def arity18[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil, ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil] {
      type Out = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]
    }

  implicit def arity19[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil, ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil] {
      type Out = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]
    }

  implicit def arity20[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil, ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil] {
      type Out = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]
    }

  implicit def arity21[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec, T21: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil, ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil] {
      type Out = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]
    }

  implicit def arity22[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec, T21: TypeCodec, T22: TypeCodec]: Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil, ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil] {
      type Out = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]
      def apply(query: CQLQuery): Out = query.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22]
    }
  // format: on
}
