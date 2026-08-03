/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import java.time.Duration
import java.time.temporal.ChronoUnit

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.api.tables.dml.where.BindPredicate
import net.nmoncho.helenus.api.tables.dml.where.BoundPredicate
import net.nmoncho.helenus.api.tables.dml.where.EntryBindPredicate
import net.nmoncho.helenus.api.tables.dml.where.Predicate
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps

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

  def bindPredicates(
      bstmt: BoundStatement,
      predicates: Seq[Predicate[_, _]],
      values: Iterator[Any],
      offset: Int = 0
  ): BoundStatement = {
    val (bound, _) = predicates.foldLeft(bstmt -> offset) {
      case ((bstmt, idx), p: BoundPredicate[Any, Any]) =>
        p.bind(bstmt, idx, p.value) -> (idx + 1)

      case ((bstmt, idx), p: EntryBindPredicate[Any, Any, Any]) =>
        val (key, value) = values.next().asInstanceOf[(Any, Any)]
        p.bind(bstmt, idx, key -> value) -> (idx + 2)

      case ((bstmt, idx), p: BindPredicate[_, Any]) =>
        p.bind(bstmt, idx, values.next()) -> (idx + 1)
    }

    bound
  }

  def bindAssignment(
      bstmt: BoundStatement,
      assignments: Seq[TableDef#Assignment[_]],
      values: Iterator[Any],
      offset: Int = 0
  ): BoundStatement =
    assignments.zipWithIndex
      .foldLeft(bstmt) {
        case (bstmt, (as: TableDef#BindAssignment[_, Any], idx)) =>
          bstmt.set(idx + offset, values.next(), as.column.codec)

        case (bstmt, (as: TableDef#BoundAssignment[_, Any], idx)) =>
          bstmt.set(idx + offset, as.value, as.column.codec)
      }

  def executeStatement(
      cql: String,
      assignments: Seq[TableDef#BoundAssignment[_, _]],
      predicates: Seq[BoundPredicate[_, _]]
  )(implicit session: CqlSession): ResultSet = {
    val pstmt = session.prepare(cql)

    session.execute(bind(pstmt, assignments, predicates))
  }

  def executeStatementAsync(
      cql: String,
      assignments: Seq[TableDef#BoundAssignment[_, _]],
      predicates: Seq[BoundPredicate[_, _]]
  )(implicit session: Future[CqlSession], ec: ExecutionContext): Future[AsyncResultSet] =
    session.flatMap { s =>
      s.prepareAsync(cql).asScala.flatMap { pstmt =>
        s.executeAsync(bind(pstmt, assignments, predicates)).asScala
      }
    }

  private def bind(
      pstmt: PreparedStatement,
      assignments: Seq[TableDef#BoundAssignment[_, _]],
      predicates: Seq[BoundPredicate[_, _]]
  ): BoundStatement = {
    val assignmentCount = assignments.length

    val bstmt = if (assignments.isEmpty) {
      pstmt.bind()
    } else {
      bindBoundAssignments(
        pstmt.bind(),
        // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
        assignments.asInstanceOf[Seq[TableDef#BoundAssignment[_, Any]]]
      )
    }

    val withPredicates = bindBoundPredicates(
      bstmt,
      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      predicates.asInstanceOf[Seq[BoundPredicate[_, _]]],
      assignmentCount
    )

    withPredicates
  }

  private def bindBoundPredicates(
      bstmt: BoundStatement,
      predicates: Seq[BoundPredicate[_, _]],
      offset: Int = 0
  ): BoundStatement =
    predicates.zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + offset, p.value)
      }

  private def bindBoundAssignments(
      bstmt: BoundStatement,
      assignments: Seq[TableDef#BoundAssignment[_, Any]],
      offset: Int = 0
  ): BoundStatement =
    assignments.zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (as, idx)) =>
        bstmt.set(idx + offset, as.value, as.column.codec)
      }
}
