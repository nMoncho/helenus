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
    target: String,
    kind: IndexKind          = IndexKind.Secondary,
    ifNotExistsFlag: Boolean = false
) {

  def ifNotExists: CreateIndex = copy(ifNotExistsFlag = true)

  def toCQL: String = {
    val ifNotExistsStr        = if (ifNotExistsFlag) " IF NOT EXISTS" else ""
    val (customStr, usingStr) = kind match {
      case IndexKind.Secondary => ("", "")
      case IndexKind.Custom(usingClass, options) =>
        val optionsStr =
          if (options.isEmpty) ""
          else
            s" WITH OPTIONS = {${options.toSeq.sortBy(_._1).map { case (k, v) => s"'$k': '$v'" }.mkString(", ")}}"
        (" CUSTOM", s" USING '$usingClass'$optionsStr")
    }
    s"CREATE$customStr INDEX$ifNotExistsStr $indexName ON ${table.fullTableName} ($target)$usingStr"
  }

  override def toString: String = toCQL
}
