/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

/** Phantom marker: clustering column with name tag `C` sorted ascending (the
  * CQL default). Used inside a table's `CK` declaration; a bare tag means the
  * same.
  */
sealed trait Asc[C]

/** Phantom marker: clustering column with name tag `C` sorted descending. */
sealed trait Desc[C]

/** A clustering column name together with its declared sort direction. */
final case class ClusteringSpec(name: String, descending: Boolean) {
  def direction: String = if (descending) "DESC" else "ASC"
}

/** A query-time ORDER BY entry: a column and its sort direction. Built with
  * `column.asc` / `column.desc`, never from raw strings.
  */
final case class ColumnOrder(column: String, descending: Boolean) {
  def direction: String = if (descending) "DESC" else "ASC"
  def toCQL: String     = s"$column $direction"
}
