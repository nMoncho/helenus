/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import java.time.Duration
import java.time.temporal.ChronoUnit

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.api.tables.dml.where.{BindPredicate, BoundPredicate, Predicate}
import shapeless.HList
import shapeless.ops.function.FnFromProduct

package object dml {

  def renderPredicates(predicates: Seq[Predicate[_, _]], prepared: Boolean): String =
    if (predicates.isEmpty) ""
    else if (prepared) s" WHERE ${predicates.map(_.forPreparedStatement).mkString(" AND ")}"
    else s" WHERE ${predicates.map(_.toCQL).mkString(" AND ")}"

  def renderUsing(ttlSeconds: Option[Duration], timestampMicros: Option[Duration]): String = {
    val usingParts = Seq(
      ttlSeconds.map(t => s"TTL ${t.toSeconds}"),
      timestampMicros.map(ts => s"TIMESTAMP ${ts.dividedBy(Duration.of(1, ChronoUnit.MICROS))}")
    ).flatten

    if (usingParts.isEmpty) "" else s" USING ${usingParts.mkString(" AND ")}"
  }

  def bindBoundPredicates(
      bstmt: BoundStatement,
      predicates: Seq[BoundPredicate[_, _]],
      offset: Int = 0
  ): BoundStatement =
    predicates.zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + offset, p.value)
      }

  def bindPredicates(
      bstmt: BoundStatement,
      predicates: Seq[Predicate[_, _]],
      values: Iterator[Any],
      offset: Int = 0
  ): BoundStatement =
    predicates.zipWithIndex.foldLeft(bstmt) {
      case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + offset, p.value)

      case (bstmt, (p: BindPredicate[_, Any], idx)) =>
        p.bind(bstmt, idx + offset, values.next())
    }

  def bindBoundAssignments(
      bstmt: BoundStatement,
      assignments: Seq[TableDef#BoundAssignment[Any]],
      offset: Int = 0
  ): BoundStatement =
    assignments.zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (as, idx)) =>
        bstmt.set(idx + offset, as.value, as.column.codec)
      }

  def bindAssignment(
      bstmt: BoundStatement,
      assignments: Seq[TableDef#Assignment[_]],
      values: Iterator[Any],
      offset: Int = 0
  ): BoundStatement =
    assignments.zipWithIndex
      .foldLeft(bstmt) {
        case (bstmt, (as: TableDef#BindAssignment[Any], idx)) =>
          bstmt.set(idx + offset, values.next(), as.column.codec)

        case (bstmt, (as: TableDef#BoundAssignment[Any], idx)) =>
          bstmt.set(idx + offset, as.value, as.column.codec)
      }

  def executeStatement(
      cql: String,
      assignments: Seq[TableDef#BoundAssignment[_]],
      predicates: Seq[BoundPredicate[_, _]]
  )(implicit session: CqlSession): ResultSet = {
    val pstmt           = session.prepare(cql)
    val assignmentCount = assignments.length

    val bstmt = if (assignments.isEmpty) {
      pstmt.bind()
    } else {
      bindBoundAssignments(
        pstmt.bind(),
        // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
        assignments.asInstanceOf[Seq[TableDef#BoundAssignment[Any]]]
      )
    }

    val withPredicates = bindBoundPredicates(
      bstmt,
      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      predicates.asInstanceOf[Seq[BoundPredicate[_, _]]],
      assignmentCount
    )

    session.execute(withPredicates)
  }

  def prepareStatement[Params <: HList, F](
      cql: String,
      assignments: Seq[TableDef#Assignment[_]],
      predicates: Seq[Predicate[_, _]]
  )(implicit session: CqlSession, fp: FnFromProduct.Aux[Params => ResultSet, F]): F = {
    val pstmt = session.prepare(cql)

    fp { params =>
      val values          = Binding.values(params).iterator
      val assignmentCount = assignments.length

      val bstmt          = bindAssignment(pstmt.bind(), assignments, values)
      val withPredicates = bindPredicates(bstmt, predicates, values, assignmentCount)

      session.execute(withPredicates)
    }
  }

}
