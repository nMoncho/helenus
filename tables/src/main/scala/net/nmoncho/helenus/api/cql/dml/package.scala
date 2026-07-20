/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.api.cql.dml.where.BindPredicate
import net.nmoncho.helenus.api.cql.dml.where.BoundPredicate
import net.nmoncho.helenus.api.cql.dml.where.Predicate

package object dml {

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

}
