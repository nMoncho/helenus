/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration
import java.time.temporal.ChronoUnit

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.api.tables.dml.where.DeleteMode
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A statement that can be placed inside a [[Batch]]: [[Insert]], [[Update]],
  * or [[Delete]]. The members are internal; a batch uses them to render each
  * statement and to bind its literal values and `?` markers.
  */
trait Batchable {

  /** Render the statement as CQL, inlining literals (`prepared = false`) or
    * emitting `?` for every value (`prepared = true`, used when the batch
    * prepares and binds each statement).
    */
  private[tables] def render(prepared: Boolean): String

}

/** Extracts a batchable statement's `?` bind-marker types as a single `HList`,
  * in writing order, so [[Batch]] can concatenate them across statements: an
  * [[Insert]] / [[Delete]] carries its markers in one type parameter, while an
  * [[Update]] splits them into SET markers then WHERE markers, joined here.
  */
sealed trait StmtParams[S] {
  type Out <: HList
}

object StmtParams {
  type Aux[S, O <: HList] = StmtParams[S] { type Out = O }

  private def instance[S, O <: HList]: Aux[S, O] = new StmtParams[S] { type Out = O }

  implicit def forInsert[T <: TableDef, P <: HList, A <: HList]: Aux[Insert[T, P, A], P] =
    instance

  implicit def forDelete[
      T <: TableDef with Singleton,
      E <: HList,
      I <: HList,
      R <: HList,
      P <: HList,
      M <: DeleteMode
  ]: Aux[Delete[T, E, I, R, P, M], P] = instance

  implicit def forUpdate[
      T <: TableDef with Singleton,
      E <: HList,
      I <: HList,
      R <: HList,
      S <: HList,
      W <: HList,
      C <: HList,
      O <: HList
  ](implicit @unused pp: Prepend.Aux[S, W, O]): Aux[Update[T, E, I, R, S, W, C], O] = instance
}

/** The kind of CQL batch: `LOGGED` (the default, atomic across partitions),
  * `UNLOGGED` (no atomicity guarantee, avoids the batch-log overhead), or
  * `COUNTER` (required when the batch updates counter columns).
  */
sealed abstract class BatchType private[tables] (
    private[tables] val keyword: String,
    private[tables] val driverType: DefaultBatchType
)

object BatchType {
  case object Logged extends BatchType("", DefaultBatchType.LOGGED)
  case object Unlogged extends BatchType("UNLOGGED", DefaultBatchType.UNLOGGED)
  case object Counter extends BatchType("COUNTER", DefaultBatchType.COUNTER)
}

/** A CQL `BATCH` grouping several [[Insert]] / [[Update]] / [[Delete]]
  * statements so they apply together. Statements are added one at a time,
  * starting from [[Batch.apply]] and chaining [[and]]:
  *
  * {{{
  * Batch(
  *   UsersTable.insert
  *     .value(UsersTable.id := fixedId)
  *     .value(UsersTable.username := ?)
  *     .value(UsersTable.age := 30)
  * ).and(
  *   SensorsTable.update
  *     .set(SensorsTable.reading := 1.0)
  *     .where(SensorsTable.deviceId === fixedId and SensorsTable.year === ? and SensorsTable.ts === 100L)
  * )
  * }}}
  *
  * The type parameter `P` accumulates every statement's `?` bind-marker types
  * (via [[StmtParams]] and shapeless `Prepend`), in writing order, so the batch
  * stays fully typed as statements are added. A batch with no `?` markers
  * (`P = HNil`) runs directly with [[execute]] / [[executeAsync]]; a batch that
  * has markers is turned by [[prepare]] / [[prepareAsync]] into a `FunctionN`
  * taking one argument per marker (in writing order, each statement in turn,
  * its assignments before its predicates) and returning a bound driver
  * `BatchStatement` ready to execute.
  *
  * [[toCQL]] renders the batch for inspection, inlining literals and showing
  * `?` for markers. The default [[BatchType]] is `LOGGED`; use [[unlogged]] /
  * [[counter]] to change it, and [[usingTimestamp]] to apply a single write
  * timestamp to every statement.
  */
final class Batch[P <: HList] private (
    private[tables] val statements: Vector[Batchable],
    val batchType: BatchType,
    val timestampMicros: Option[Duration]
) {

  /** Add another statement, concatenating its `?` markers onto this batch's. */
  def and[S <: Batchable, SP <: HList, P2 <: HList](stmt: S)(
      implicit @unused sp: StmtParams.Aux[S, SP],
      @unused pre: Prepend.Aux[P, SP, P2]
  ): Batch[P2] =
    new Batch[P2](statements :+ stmt, batchType, timestampMicros)

  /** Switch this batch to the given [[BatchType]]. */
  def withBatchType(batchType: BatchType): Batch[P] =
    new Batch[P](statements, batchType, timestampMicros)

  /** Make this an `UNLOGGED` batch. */
  def unlogged: Batch[P] = withBatchType(BatchType.Unlogged)

  /** Make this a `COUNTER` batch (required when updating counter columns). */
  def counter: Batch[P] = withBatchType(BatchType.Counter)

  /** Apply a single write timestamp (`USING TIMESTAMP`) to every statement. */
  def usingTimestamp(micros: Duration): Batch[P] =
    new Batch[P](statements, batchType, Some(micros))

  /** Render the batch as `BEGIN [UNLOGGED|COUNTER] BATCH ... APPLY BATCH`,
    * inlining literal values and showing `?` for bind markers.
    */
  def toCQL: String = {
    val typeStr  = if (batchType.keyword.isEmpty) "" else s" ${batchType.keyword}"
    val usingStr = renderUsing(None, timestampMicros)
    val body     = statements.map(s => s"  ${s.render(prepared = false)};").mkString("\n")

    s"BEGIN$typeStr BATCH$usingStr\n$body\nAPPLY BATCH"
  }

  override def toString: String = toCQL

  /** Run the batch. Available only when no statement has an unbound `?` marker
    * (`P = HNil`); otherwise use [[prepare]].
    */
  def execute()(implicit session: CqlSession, ev: P =:= HNil): ResultSet =
    session.execute(
      build(statements.map(s => s -> session.prepare(s.render(prepared = true))), ev.flip(HNil))
    )

  /** Run the batch asynchronously. Available only when `P = HNil`. */
  def executeAsync()(
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      ev: P =:= HNil
  ): Future[AsyncResultSet] =
    session.flatMap { s =>
      prepareChildrenAsync(s).flatMap(prepared =>
        s.executeAsync(build(prepared, ev.flip(HNil))).asScala
      )
    }

  /** Prepare the batch into a `FunctionN` taking one argument per `?` marker
    * (in writing order) and returning a bound driver `BatchStatement`, ready to
    * pass to `session.execute` / `session.executeAsync`. The child statements
    * are prepared once, here, and the returned function can be reused.
    */
  def prepare[F](
      implicit session: CqlSession,
      fp: FnFromProduct.Aux[P => BatchStatement, F]
  ): F = {
    val prepared = statements.map(s => s -> session.prepare(s.render(prepared = true)))
    fp.apply((params: P) => build(prepared, params))
  }

  /** Asynchronous [[prepare]]: prepares the child statements, then completes
    * with the same `FunctionN`.
    */
  def prepareAsync[F](
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      fp: FnFromProduct.Aux[P => BatchStatement, F]
  ): Future[F] =
    session.flatMap { s =>
      prepareChildrenAsync(s).map(prepared => fp.apply((params: P) => build(prepared, params)))
    }

  private def prepareChildrenAsync(
      session: CqlSession
  )(implicit ec: ExecutionContext): Future[Vector[(Batchable, PreparedStatement)]] =
    // Prepare in order so the arguments iterator is later consumed left to right.
    statements.foldLeft(Future.successful(Vector.empty[(Batchable, PreparedStatement)])) {
      (acc, stmt) =>
        acc.flatMap { prepared =>
          session
            .prepareAsync(stmt.render(prepared = true))
            .asScala
            .map(ps => prepared :+ (stmt -> ps))
        }
    }

  /** Assemble the driver `BatchStatement` from the prepared child statements,
    * binding each one's literals and pulling its `?` values from `params`.
    */
  private def build(
      prepared: Vector[(Batchable, PreparedStatement)],
      params: P
  ): BatchStatement = {
    val values  = Binding.values(params).iterator
    val builder = BatchStatement.builder(batchType.driverType)
    prepared.foreach { case (stmt, pstmt) => builder.addStatement(bindChild(pstmt, stmt, values)) }
    timestampMicros.foreach(ts => builder.setQueryTimestamp(toMicros(ts)))

    builder.build()
  }

  /** Bind one statement into a [[BoundStatement]]: its assignments occupy the
    * first positions, its predicates follow (see [[bindAssignment]] /
    * [[bindPredicates]]), pulling `?` values from `values` and taking literals
    * from the statement itself.
    */
  private def bindChild(
      pstmt: PreparedStatement,
      stmt: Batchable,
      values: Iterator[Any]
  ): BoundStatement = {
    val (assignments, predicates) = stmt match {
      case Update(_, assignments, predicates, _, _, _) => assignments -> predicates
      case Delete(_, _, predicates, _, _) => Seq() -> predicates
      case Insert(_, assignments, _, _, _) => assignments -> Seq()
      case _ => Seq() -> Seq()
    }

    val bound = bindAssignment(pstmt.bind(), assignments, values)
    bindPredicates(bound, predicates, values, assignments.length)
  }

  private def toMicros(d: Duration): Long = d.dividedBy(Duration.of(1, ChronoUnit.MICROS))
}

object Batch {

  /** Start a `LOGGED` batch with its first statement; add more with [[Batch.and]]. */
  def apply[S <: Batchable, SP <: HList](stmt: S)(
      implicit @unused sp: StmtParams.Aux[S, SP]
  ): Batch[SP] = new Batch[SP](Vector(stmt), BatchType.Logged, None)

  /** Start a `LOGGED` batch (explicit alias for [[apply]]). */
  def logged[S <: Batchable, SP <: HList](stmt: S)(
      implicit @unused sp: StmtParams.Aux[S, SP]
  ): Batch[SP] = new Batch[SP](Vector(stmt), BatchType.Logged, None)

  /** Start an `UNLOGGED` batch with its first statement. */
  def unlogged[S <: Batchable, SP <: HList](stmt: S)(
      implicit @unused sp: StmtParams.Aux[S, SP]
  ): Batch[SP] = new Batch[SP](Vector(stmt), BatchType.Unlogged, None)

  /** Start a `COUNTER` batch with its first statement (for counter-column updates). */
  def counter[S <: Batchable, SP <: HList](stmt: S)(
      implicit @unused sp: StmtParams.Aux[S, SP]
  ): Batch[SP] = new Batch[SP](Vector(stmt), BatchType.Counter, None)
}
