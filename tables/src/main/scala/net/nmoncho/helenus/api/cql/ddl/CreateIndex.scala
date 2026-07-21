/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package ddl

/** @param target the CQL index target: a bare column name (indexes a
  *               scalar column, or a collection's values), or a wrapped
  *               form such as `KEYS(col)` (a map's keys) / `ENTRIES(col)`.
  * @param kind   [[IndexKind.Secondary]] (the default, `CREATE INDEX`) or
  *               [[IndexKind.Custom]] (`CREATE CUSTOM INDEX ... USING '...'`).
  */
final case class CreateIndex(
    table: TableDef,
    indexName: String,
    columnName: String,
    ifNotExistsFlag: Boolean = false
) {

  def ifNotExists: CreateIndex = copy(ifNotExistsFlag = true)

  def toCQL: String = {
    val ifNotExistsStr = if (ifNotExistsFlag) " IF NOT EXISTS" else ""
    s"CREATE INDEX$ifNotExistsStr $indexName ON ${table.fullTableName} ($columnName)"
  }

  override def toString: String = toCQL
}
