/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package ddl

final case class DropTable(table: TableDef, ifExistsFlag: Boolean = false) {

  def ifExists: DropTable = copy(ifExistsFlag = true)

  def toCQL: String = {
    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""
    s"DROP TABLE$ifExistsStr ${table.fullTableName}"
  }

  override def toString: String = toCQL
}
