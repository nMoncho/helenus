/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.tables.dml.where.CanInsert
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A typed INSERT builder.
  *
  * @tparam T        the singleton type of the table
  * @tparam Params   type-level `HList` of the bound value types collected from
  *                  `?` markers (`value(col := ?)`), in writing order
  * @tparam Assigned type-level `HList` accumulating the field tag of every
  *                  column assigned (via either `value` overload), in writing
  *                  order. It lets [[execute]] / [[prepare]] / [[prepareAsync]]
  *                  require [[CanInsert]] evidence that the whole primary key
  *                  is set, exactly as CQL demands (non-key columns optional).
  *
  *                A statement with `?` markers becomes a function via [[prepare]], taking
  *                one argument per marker and returning the rendered CQL.
  */
final case class Insert[T <: TableDef, Params <: HList, Assigned <: HList](
    table: T,
    assignments: Seq[TableDef#Assignment[_]] = Seq.empty,
    ttlSeconds: Option[Duration]             = None,
    timestampMicros: Option[Duration]        = None,
    ifNotExistsFlag: Boolean                 = false
) {

  def value[Col, V](
      assignment: table.BoundAssignment[Col, V]
  ): Insert[T, Params, Col :: Assigned] =
    new Insert[T, Params, Col :: Assigned](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  /** A bound value (`col := ?`), appended to the parameter list. */
  def value[Col, V, P2 <: HList](
      assignment: table.BindAssignment[Col, V]
  )(implicit @unused pp: Prepend.Aux[Params, V :: HNil, P2]): Insert[T, P2, Col :: Assigned] =
    new Insert[T, P2, Col :: Assigned](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  def usingTTL(seconds: Duration): Insert[T, Params, Assigned] =
    copy(ttlSeconds = Some(seconds))

  def usingTimestamp(micros: Duration): Insert[T, Params, Assigned] =
    copy(timestampMicros = Some(micros))

  def ifNotExists: Insert[T, Params, Assigned] =
    copy(ifNotExistsFlag = true)

  private[tables] def render(prepared: Boolean = false): String = {
    val (cols, vals) = assignments.foldLeft(Vector.empty[String] -> Vector.empty[String]) {
      case ((cols, vals), as) =>
        val (col, colVal) = as match {
          case simple: TableDef#BoundAssignment[_, _] if !prepared =>
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
      @unused canInsert: CanInsert[table.PK, table.CK, Assigned]
  ): ResultSet =
    executeStatement(
      render(prepared = true),
      // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
      assignments.asInstanceOf[Seq[TableDef#BoundAssignment[_, Any]]],
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
      @unused canInsert: CanInsert[table.PK, table.CK, Assigned]
  ): to.Out =
    to(render(prepared = true), assignments, Nil)

  def prepareAsync[F](
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      to: ToPrepared[Params],
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F],
      @unused canInsert: CanInsert[table.PK, table.CK, Assigned]
  ): Future[to.Out] =
    to.async(
      render(prepared = true),
      assignments,
      Nil
    )

  override def toString: String = toCQL
}

object Insert {

  def apply[T <: TableDef](table: T): Insert[T, HNil, HNil] = new Insert[T, HNil, HNil](table)
}
