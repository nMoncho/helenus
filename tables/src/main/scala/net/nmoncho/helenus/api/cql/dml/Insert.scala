/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import scala.annotation.unused

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
    assignments: Seq[TableDef#Assignment] = Seq.empty,
    ttlSeconds: Option[Int]               = None,
    timestampMicros: Option[Long]         = None,
    ifNotExistsFlag: Boolean              = false
) {

  def value(assignment: table.Assignment): Insert[T, Params] =
    copy(assignments = assignments :+ assignment)

  /** A bound value (`col := ?`), appended to the parameter list. */
  def value[V, P2 <: HList](
      assignment: table.BoundAssignment[V]
  )(implicit @unused pp: Prepend.Aux[Params, V :: HNil, P2]): Insert[T, P2] =
    new Insert[T, P2](
      table,
      assignments :+ assignment,
      ttlSeconds,
      timestampMicros,
      ifNotExistsFlag
    )

  def usingTTL(seconds: Int): Insert[T, Params] =
    copy(ttlSeconds = Some(seconds))

  def usingTimestamp(micros: Long): Insert[T, Params] =
    copy(timestampMicros = Some(micros))

  def ifNotExists: Insert[T, Params] =
    copy(ifNotExistsFlag = true)

  def toCQL: String = {
    // TODO just like Update, add this requirement at type-level
    require(assignments.nonEmpty, "INSERT must have at least one column value")

    val cols = assignments.map(_.column).mkString(", ")
    val vals = assignments.map(_.value).mkString(", ")

    val ifNotExistsStr = if (ifNotExistsFlag) " IF NOT EXISTS" else ""

    val usingParts = Seq(
      ttlSeconds.map(t => s"TTL $t"),
      timestampMicros.map(ts => s"TIMESTAMP $ts")
    ).flatten

    val usingStr = if (usingParts.isEmpty) "" else s" USING ${usingParts.mkString(" AND ")}"

    s"INSERT INTO ${table.fullTableName} ($cols) VALUES ($vals)$ifNotExistsStr$usingStr"
  }

  /** Turn an insert containing `?` markers into a `FunctionN` taking one
    * argument per marker (typed as the bound column, in writing order) and
    * returning the rendered CQL.
    */
  def toFunction[F](implicit fp: FnFromProduct.Aux[Params => String, F]): F =
    fp { params =>
      val values = Binding.values(params).iterator
      copy(assignments = assignments.map {
        case hole: AssignmentHole => hole.fill(values.next())
        case complete => complete
      }).toCQL
    }

  override def toString: String = toCQL
}

object Insert {

  def apply[T <: TableDef](table: T): Insert[T, HNil] = new Insert[T, HNil](table)
}
