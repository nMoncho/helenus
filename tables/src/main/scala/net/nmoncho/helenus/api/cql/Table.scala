package net.nmoncho.helenus.api.cql

import net.nmoncho.helenus.api.cql.ddl.DropTable

abstract class TableDef(val keyspace: String, val tableName: String) {

  def fullTableName: String = s"${keyspace}.${tableName}"

  def drop: DropTable = DropTable(this)

}
