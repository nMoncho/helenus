/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import net.nmoncho.helenus.api.cql.dml.where.Predicate

final case class Select[T <: TableDef with Singleton](
    table: T,
    columns: Seq[String],
    keyColumns: Seq[String],
    predicates: Seq[Predicate]       = Seq.empty,
    limitValue: Option[Int]          = None,
    orderByClauses: Seq[ColumnOrder] = Seq.empty
) {

  def limit(n: Int): Select[T] = copy(limitValue = Some(n))

  /** Add ORDER BY clauses, built from a column's `asc` / `desc` methods, e.g.
    * `orderBy(username.desc)` or `orderBy(year.asc, ts.desc)`. A bare column
    * defaults to ascending via the other overload.
    */
  def orderBy(orders: ColumnOrder*): Select[T] =
    copy(orderByClauses = orderByClauses ++ orders)

  /** Order ascending by the given column (the CQL default direction). */
  def orderBy(col: table.Column[_]): Select[T] =
    orderBy(col.asc)

  def toCQL: String = Select.render(this, allowFiltering = false)

  override def toString: String = toCQL
}

object Select {

  def apply[T <: TableDef with Singleton](
      table: T,
      columns: Seq[String],
      keyColumns: Seq[String]
  ): Select[T] =
    new Select[T](table, columns, keyColumns)

  private def render[T <: TableDef with Singleton](
      s: Select[T],
      allowFiltering: Boolean
  ): String = {
    val colStr = if (s.columns.isEmpty) "*" else s.columns.mkString(", ")

    val whereStr =
      if (s.predicates.isEmpty) ""
      else s" WHERE ${orderedPredicates(s).map(_.toCQL).mkString(" AND ")}"

    val orderStr =
      if (s.orderByClauses.isEmpty) ""
      else s" ORDER BY ${s.orderByClauses.map(_.toCQL).mkString(", ")}"

    val limitStr          = s.limitValue.map(l => s" LIMIT $l").getOrElse("")
    val allowFilteringStr = if (allowFiltering) " ALLOW FILTERING" else ""

    s"SELECT $colStr FROM ${s.table.fullTableName}$whereStr$orderStr$limitStr$allowFilteringStr"
  }

  /** Reorders predicates to CQL order regardless of how the user chained them:
    * partition-key columns first (in key order), then clustering columns (in
    * declaration order), then everything else in insertion order. The sort is
    * stable, so several predicates on the same column (a slice) keep their
    * relative order. Bound parameters are filled in writing order BEFORE this
    * reordering, so `?` positions and argument positions always agree.
    */
  private def orderedPredicates[T <: TableDef with Singleton](
      s: Select[T]
  ): Seq[Predicate] = {
    val keyIndex: Map[String, Int] = s.keyColumns.zipWithIndex.toMap
    s.predicates.sortBy(p => keyIndex.getOrElse(p.column, Int.MaxValue))
  }
}
