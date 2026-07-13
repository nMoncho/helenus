/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml



final case class Select[T <: TableDef with Singleton](
    table: T,
    columns: Seq[String],
    keyColumns: Seq[String],
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
    val colStr   = if (s.columns.isEmpty) "*" else s.columns.mkString(", ")

    val whereStr = ""

    val orderStr =
      if (s.orderByClauses.isEmpty) ""
      else s" ORDER BY ${s.orderByClauses.map(_.toCQL).mkString(", ")}"

    val limitStr          = s.limitValue.map(l => s" LIMIT $l").getOrElse("")
    val allowFilteringStr = if (allowFiltering) " ALLOW FILTERING" else ""

    s"SELECT $colStr FROM ${s.table.fullTableName}$whereStr$orderStr$limitStr$allowFilteringStr"
  }
}
