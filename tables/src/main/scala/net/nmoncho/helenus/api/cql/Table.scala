/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.cql.ddl.DropTable

abstract class TableDef(val keyspace: String, val tableName: String) {

  def fullTableName: String = s"${keyspace}.${tableName}"

  // --------------- DDL ----------------

  def drop: DropTable = DropTable(this)

  /** A typed column of this table. Instances are obtained with
    * `column("fieldName")`, which checks the field against the mapped case
    * class; `fieldName` is the case-class field, `name` the CQL column name
    * produced by the table's [[net.nmoncho.helenus.api.ColumnNamingScheme]].
    */
  class Column[T](val fieldName: String, val name: String, val frozen: Boolean)(
      implicit codec: TypeCodec[T]
  ) {

    override def toString: String =
      s"Column($fieldName -> $name: ${codec.getCqlType.asCql(frozen, false)})"
  }
}
