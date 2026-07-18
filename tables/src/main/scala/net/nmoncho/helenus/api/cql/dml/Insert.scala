/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import java.time.Duration
import java.time.temporal.ChronoUnit

import scala.annotation.unused

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
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
  *
  * A statement with `?` markers becomes a function via [[toFunction]], taking
  * one argument per marker and returning the rendered CQL.
  */
final case class Insert[T <: TableDef, Params <: HList](
    table: T,
    assignments: Seq[TableDef#Assignment[_]] = Seq.empty,
    ttlSeconds: Option[Duration]             = None,
    timestampMicros: Option[Duration]        = None,
    ifNotExistsFlag: Boolean                 = false
) {

  def value(assignment: table.Assignment[_]): Insert[T, Params] =
    copy(assignments = assignments :+ assignment)

  /** A bound value (`col := ?`), appended to the parameter list. */
  def value[V, P2 <: HList](
      assignment: table.BindAssignment[V]
  )(implicit @unused pp: Prepend.Aux[Params, V :: HNil, P2]): Insert[T, P2] =
    new Insert[T, P2](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  def usingTTL(seconds: Duration): Insert[T, Params] =
    copy(ttlSeconds = Some(seconds))

  def usingTimestamp(micros: Duration): Insert[T, Params] =
    copy(timestampMicros = Some(micros))

  def ifNotExists: Insert[T, Params] =
    copy(ifNotExistsFlag = true)

  private[cql] def innerToCQL(prepared: Boolean = false): String = {
    // TODO just like Update, add this requirement at type-level
    require(assignments.nonEmpty, "INSERT must have at least one column value")

    val (cols, vals) = assignments.foldLeft(Vector.empty[String] -> Vector.empty[String]) {
      case ((cols, vals), as) =>
        val (col, colVal) = as match {
          case simple: TableDef#SimpleAssignment[_] if !prepared =>
            simple.column.name -> simple.column.codec.format(simple.value)

          case _ =>
            as.column.name -> "?"
        }

        (cols :+ col) -> (vals :+ colVal)
    }

    val ifNotExistsStr = if (ifNotExistsFlag) " IF NOT EXISTS" else ""

    val usingParts = Seq(
      ttlSeconds.map(t => s"TTL ${t.toSeconds}"),
      timestampMicros.map(ts => s"TIMESTAMP ${ts.dividedBy(Duration.of(1, ChronoUnit.MICROS))}")
    ).flatten

    val usingStr = if (usingParts.isEmpty) "" else s" USING ${usingParts.mkString(" AND ")}"

    s"INSERT INTO ${table.fullTableName} (${cols.mkString(", ")}) VALUES (${vals.mkString(", ")})$ifNotExistsStr$usingStr"
  }

  def toCQL: String = innerToCQL(prepared = false)

  def execute()(
      implicit session: CqlSession,
      @unused noUnboundParams: Params =:= HNil
  ): ResultSet = {
    val pstmt = session.prepare(innerToCQL(prepared = true))

    // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
    val bstmt = assignments
      .asInstanceOf[Seq[TableDef#SimpleAssignment[Any]]]
      .zipWithIndex
      .foldLeft(pstmt.bind()) { case (bstmt, (as, idx)) =>
        bstmt.set(idx, as.value, as.column.codec)
      }

    session.execute(bstmt)
  }

  /** Turn an insert containing `?` markers into a `FunctionN` taking one
    * argument per marker (typed as the bound column, in writing order) and
    * returning the rendered CQL.
    */
  def toFunction[F](
      implicit session: CqlSession,
      fp: FnFromProduct.Aux[Params => ResultSet, F]
  ): F = {
    val pstmt = session.prepare(innerToCQL(prepared = true))

    fp { params =>
      val values = Binding.values(params).iterator

      val bstmt = assignments.zipWithIndex
        .foldLeft(pstmt.bind()) {
          case (bstmt, (as: TableDef#BindAssignment[Any], idx)) =>
            bstmt.set(idx, values.next(), as.column.codec)

          case (bstmt, (as: TableDef#SimpleAssignment[Any], idx)) =>
            bstmt.set(idx, as.value, as.column.codec)
        }

      session.execute(bstmt)
    }
  }

  override def toString: String = toCQL
}

object Insert {

  def apply[T <: TableDef](table: T): Insert[T, HNil] = new Insert[T, HNil](table)
}
