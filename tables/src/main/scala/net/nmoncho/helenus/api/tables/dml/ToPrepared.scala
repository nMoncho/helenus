/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.ScalaBoundStatement
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import net.nmoncho.helenus.api.cql.StatementOptions
import net.nmoncho.helenus.api.tables.dml.where.Predicate
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps
import net.nmoncho.helenus.internal.cql._
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function
import shapeless.ops.function.FnFromProduct

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

  /** Converts to a ScalaPreparedStatement
    *
    * @param cql query to prepare
    * @param assignments assignments, if any
    * @param predicates predicates, if any
    * @param session session to prepare query against
    * @param fp function creator
    * @tparam F function type derived from params
    * @return
    */
  def apply[F](
      cql: String,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(
      implicit session: CqlSession,
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
  ): Out

  /** Converts to a ScalaPreparedStatement
    *
    * @param cql query to prepare
    * @param assignments assignments, if any
    * @param predicates predicates, if any
    * @param session session to prepare query against
    * @param fp function creator
    * @tparam F function type derived from params
    * @return
    */
  def async[F](
      cql: String,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
  ): Future[Out]

  protected def prepare[F](
      cql: String,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(
      implicit session: CqlSession,
      fp: FnFromProduct.Aux[Params => BoundStatement, F]
  ): (PreparedStatement, F) = {
    val pstmt = session.prepare(cql)

    pstmt -> bind(pstmt, assignments, predicates)
  }

  protected def prepareAsync[F](
      cql: String,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      fp: FnFromProduct.Aux[Params => BoundStatement, F]
  ): Future[(PreparedStatement, F)] =
    session.flatMap(implicit s =>
      s.prepareAsync(cql).asScala.map { pstmt =>
        pstmt -> bind(pstmt, assignments, predicates)
      }
    )

  protected def bind[F](
      pstmt: PreparedStatement,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(
      implicit fp: FnFromProduct.Aux[Params => BoundStatement, F]
  ): F =
    fp { params =>
      val values          = Binding.values(params).iterator
      val assignmentCount = assignments.length

      val bstmt          = bindAssignment(pstmt.bind(), assignments, values)
      val withPredicates = bindPredicates(bstmt, predicates, values, assignmentCount)

      withPredicates
    }
}

object ToPrepared {
  type Aux[Params <: HList, Out0] = ToPrepared[Params] { type Out = Out0 }

  // format: off
  implicit val arity0: Aux[HNil, ScalaPreparedStatementUnit[Row]] =
    new ToPrepared[HNil] {
      type Out = ScalaPreparedStatementUnit[Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: () => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatementUnit[PsOut] =
        new ScalaPreparedStatementUnit[PsOut](pstmt, m, o) {
          override def apply(): ScalaBoundStatement[PsOut]    = ScalaBoundStatement(this, fn())
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatementUnit[Row] = {
        val (pstmt, fn) = prepare(cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatementUnit[Row]] = {
        prepareAsync(cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity1[T1](implicit tc1: TypeCodec[T1]): Aux[T1 :: HNil, ScalaPreparedStatement1[T1, Row]] =

    new ToPrepared[T1 :: HNil] {
      type Out = ScalaPreparedStatement1[T1, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: T1 => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement1[T1, PsOut] =
        new ScalaPreparedStatement1[T1, PsOut](pstmt, m, o, tc1) {
          override def apply(t1: T1): ScalaBoundStatement[PsOut]    = ScalaBoundStatement(this, fn(t1))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement1[T1, Row] = {
        val (pstmt, fn) = prepare[T1 => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement1[T1, Row]] = {
        prepareAsync[T1 => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity2[T1, T2](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2]): Aux[T1 :: T2 :: HNil, ScalaPreparedStatement2[T1, T2, Row]] =
    new ToPrepared[T1 :: T2 :: HNil] {
      type Out = ScalaPreparedStatement2[T1, T2, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement2[T1, T2, PsOut] =
        new ScalaPreparedStatement2[T1, T2, PsOut](pstmt, m, o, tc1, tc2) {
          override def apply(t1: T1, t2: T2): ScalaBoundStatement[PsOut]    = ScalaBoundStatement(this, fn(t1, t2))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement2[T1, T2, Row] = {
        val (pstmt, fn) = prepare[(T1, T2) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement2[T1, T2, Row]] = {
        prepareAsync[(T1, T2) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity3[T1, T2, T3](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3]): Aux[T1 :: T2 :: T3 :: HNil, ScalaPreparedStatement3[T1, T2, T3, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: HNil] {
      type Out = ScalaPreparedStatement3[T1, T2, T3, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement3[T1, T2, T3, PsOut] =
        new ScalaPreparedStatement3[T1, T2, T3, PsOut](pstmt, m, o, tc1, tc2, tc3) {
          override def apply(t1: T1, t2: T2, t3: T3): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement3[T1, T2, T3, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement3[T1, T2, T3, Row]] = {
        prepareAsync[(T1, T2, T3) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity4[T1, T2, T3, T4](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4]): Aux[T1 :: T2 :: T3 :: T4 :: HNil, ScalaPreparedStatement4[T1, T2, T3, T4, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: HNil] {
      type Out = ScalaPreparedStatement4[T1, T2, T3, T4, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement4[T1, T2, T3, T4, PsOut] =
        new ScalaPreparedStatement4[T1, T2, T3, T4, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement4[T1, T2, T3, T4, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Row]] = {
        prepareAsync[(T1, T2, T3, T4) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity5[T1, T2, T3, T4, T5](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: HNil, ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: HNil] {
      type Out = ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement5[T1, T2, T3, T4, T5, PsOut] =
        new ScalaPreparedStatement5[T1, T2, T3, T4, T5, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity6[T1, T2, T3, T4, T5, T6](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil, ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil] {
      type Out = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, PsOut] =
        new ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity7[T1, T2, T3, T4, T5, T6, T7](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil, ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil] {
      type Out = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, PsOut] =
        new ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity8[T1, T2, T3, T4, T5, T6, T7, T8](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil, ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil] {
      type Out = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, PsOut] =
        new ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity9[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil, ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil] {
      type Out = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, PsOut] =
        new ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil, ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil] {
      type Out = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, PsOut] =
        new ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil, ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil] {
      type Out = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, PsOut] =
        new ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil, ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil] {
      type Out = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, PsOut] =
        new ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil, ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil] {
      type Out = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, PsOut] =
        new ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil, ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil] {
      type Out = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, PsOut] =
        new ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil, ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil] {
      type Out = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, PsOut] =
        new ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil, ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil] {
      type Out = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, PsOut] =
        new ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil, ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil] {
      type Out = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, PsOut] =
        new ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17], tc18: TypeCodec[T18]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil, ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil] {
      type Out = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, PsOut] =
        new ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17, tc18) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17], tc18: TypeCodec[T18], tc19: TypeCodec[T19]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil, ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil] {
      type Out = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, PsOut] =
        new ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17, tc18, tc19) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17], tc18: TypeCodec[T18], tc19: TypeCodec[T19], tc20: TypeCodec[T20]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil, ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil] {
      type Out = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, PsOut] =
        new ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17, tc18, tc19, tc20) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17], tc18: TypeCodec[T18], tc19: TypeCodec[T19], tc20: TypeCodec[T20], tc21: TypeCodec[T21]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil, ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil] {
      type Out = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, PsOut] =
        new ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17, tc18, tc19, tc20, tc21) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }

  implicit def arity22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](implicit tc1: TypeCodec[T1], tc2: TypeCodec[T2], tc3: TypeCodec[T3], tc4: TypeCodec[T4], tc5: TypeCodec[T5], tc6: TypeCodec[T6], tc7: TypeCodec[T7], tc8: TypeCodec[T8], tc9: TypeCodec[T9], tc10: TypeCodec[T10], tc11: TypeCodec[T11], tc12: TypeCodec[T12], tc13: TypeCodec[T13], tc14: TypeCodec[T14], tc15: TypeCodec[T15], tc16: TypeCodec[T16], tc17: TypeCodec[T17], tc18: TypeCodec[T18], tc19: TypeCodec[T19], tc20: TypeCodec[T20], tc21: TypeCodec[T21], tc22: TypeCodec[T22]): Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil, ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]] =
    new ToPrepared[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil] {
      type Out = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]

      private def create[PsOut](pstmt: PreparedStatement, fn: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => BoundStatement, m: RowMapper[PsOut], o: StatementOptions): ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, PsOut] =
        new ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, PsOut](pstmt, m, o, tc1, tc2, tc3, tc4, tc5, tc6, tc7, tc8, tc9, tc10, tc11, tc12, tc13, tc14, tc15, tc16, tc17, tc18, tc19, tc20, tc21, tc22) {
          override def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): ScalaBoundStatement[PsOut] = ScalaBoundStatement(this, fn(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))
          override def withOptions(options: StatementOptions): Self = create(pstmt, fn, m, options)
          override def as[Out2](implicit ev: PsOut =:= Row, m2: RowMapper[Out2]): AsOut[Out2] = create(pstmt, fn, m2, o)
        }

      override def apply[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: CqlSession, fp: FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil => ScalaBoundStatement[Row], F]): ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row] = {
        val (pstmt, fn) = prepare[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => BoundStatement](cql, assignments, predicates)
        create(pstmt, fn, RowMapper.identity, StatementOptions.default)
      }

      override def async[F](cql: String, assignments: Seq[TableDef#Assignment[_]], predicates: Seq[Predicate[_, _]])(implicit session: Future[CqlSession], ec: ExecutionContext, fp: function.FnFromProduct.Aux[T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: T9 :: T10 :: T11 :: T12 :: T13 :: T14 :: T15 :: T16 :: T17 :: T18 :: T19 :: T20 :: T21 :: T22 :: HNil => ScalaBoundStatement[Row], F]): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]] = {
        prepareAsync[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) => BoundStatement](cql, assignments, predicates).map { case (pstmt, fn) =>
          create(pstmt, fn, RowMapper.identity, StatementOptions.default)
        }
      }
    }
  // format: on
}
