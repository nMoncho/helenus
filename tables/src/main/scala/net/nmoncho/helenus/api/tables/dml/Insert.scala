/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration

import scala.annotation.implicitNotFound
import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.ScalaBoundStatement
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A typed INSERT builder.
  *
  * @tparam T      the singleton type of the table
  * @tparam Params type-level `HList` of the bound value types collected from
  *                `?` markers (`value(col := ?)`), in writing order
  * @tparam Cols   type-level `HList` accumulating one marker per column value
  *                added (via either `value` overload), in writing order. Its
  *                only purpose is to make "at least one value" a compile-time
  *                fact: it starts at `HNil` and every `value(...)` prepends an
  *                element, so [[execute]] / [[prepare]] / [[prepareAsync]] can
  *                require [[NonEmpty]] evidence that has no instance for `HNil`.
  *
  *                A statement with `?` markers becomes a function via [[prepare]], taking
  *                one argument per marker and returning the rendered CQL.
  */
final case class Insert[T <: TableDef, Params <: HList, Cols <: HList](
    table: T,
    assignments: Seq[TableDef#Assignment[_]] = Seq.empty,
    ttlSeconds: Option[Duration]             = None,
    timestampMicros: Option[Duration]        = None,
    ifNotExistsFlag: Boolean                 = false
) {

  def value[V](assignment: table.Assignment[V]): Insert[T, Params, V :: Cols] =
    new Insert[T, Params, V :: Cols](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  /** A bound value (`col := ?`), appended to the parameter list. */
  def value[V, P2 <: HList](
      assignment: table.BindAssignment[V]
  )(implicit @unused pp: Prepend.Aux[Params, V :: HNil, P2]): Insert[T, P2, BindMarker :: Cols] =
    new Insert[T, P2, BindMarker :: Cols](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  def usingTTL(seconds: Duration): Insert[T, Params, Cols] =
    copy(ttlSeconds = Some(seconds))

  def usingTimestamp(micros: Duration): Insert[T, Params, Cols] =
    copy(timestampMicros = Some(micros))

  def ifNotExists: Insert[T, Params, Cols] =
    copy(ifNotExistsFlag = true)

  private[tables] def render(prepared: Boolean = false): String = {
    val (cols, vals) = assignments.foldLeft(Vector.empty[String] -> Vector.empty[String]) {
      case ((cols, vals), as) =>
        val (col, colVal) = as match {
          case simple: TableDef#BoundAssignment[_] if !prepared =>
            simple.column.name -> simple.column.codec.format(simple.value)

          case _ =>
            as.column.name -> "?"
        }

        (cols :+ col) -> (vals :+ colVal)
    }

    val usingStr       = renderUsing(ttlSeconds, timestampMicros)
    val ifNotExistsStr = if (ifNotExistsFlag) " IF NOT EXISTS" else ""

    s"INSERT INTO ${table.fullTableName} (${cols.mkString(", ")}) VALUES (${vals.mkString(", ")})$ifNotExistsStr$usingStr"
  }

  def toCQL: String = render(prepared = false)

  def execute()(
      implicit session: CqlSession,
      @unused noUnboundParams: Params =:= HNil,
      @unused nonEmpty: NonEmpty[Cols]
  ): ResultSet =
    executeStatement(
      render(prepared = true),
      // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
      assignments.asInstanceOf[Seq[TableDef#BoundAssignment[Any]]],
      Nil
    )

  /** Turn an insert containing `?` markers into a `FunctionN` taking one
    * argument per marker (typed as the bound column, in writing order) and
    * returning the rendered CQL.
    */
  def prepare[F](
      implicit session: CqlSession,
      to: ToPrepared[Params],
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F],
      @unused nonEmpty: NonEmpty[Cols]
  ): to.Out =
    to(render(prepared = true), assignments, Nil)

  def prepareAsync[F](
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      to: ToPrepared[Params],
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F],
      @unused nonEmpty: NonEmpty[Cols]
  ): Future[to.Out] =
    session.map { implicit s =>
      to(render(prepared = true), assignments, Nil)
    }

  override def toString: String = toCQL
}

object Insert {

  def apply[T <: TableDef](table: T): Insert[T, HNil, HNil] = new Insert[T, HNil, HNil](table)
}

/** Evidence that at least one column value has been added to an INSERT (i.e.
  * the accumulated `Cols` list is non-empty). There is no instance for `HNil`,
  * so [[Insert.execute]] / [[Insert.prepare]] / [[Insert.prepareAsync]] fail to
  * compile until the first `value(...)` is supplied.
  */
@implicitNotFound(
  "This INSERT has no column values and cannot be executed. " +
    "CQL requires an INSERT to set at least one column: add a value(...) " +
    "before calling execute, prepare, or prepareAsync."
)
sealed trait NonEmpty[L <: HList]

object NonEmpty {

  implicit def cons[H, T <: HList]: NonEmpty[H :: T] = new NonEmpty[H :: T] {}
}
