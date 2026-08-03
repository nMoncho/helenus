/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps

/** A `CREATE TABLE` statement derived from a [[Table]] definition.
  *
  * Instances are not built by hand: obtain one from [[Table.create]], which
  * fills every field from the mapped case class and the table's `PK` / `CK`
  * declarations. Rendering the CQL always emits the full column list, the
  * `PRIMARY KEY` clause (parenthesising the partition key when it is composite
  * or when clustering columns follow), and a `CLUSTERING ORDER BY` clause only
  * when at least one clustering column is declared descending (ASC being the
  * CQL default).
  *
  * {{{
  * UsersTable.create.ifNotExists.execute()
  * }}}
  *
  * @param table             the table this statement creates.
  * @param columns           every column to declare, registered plus computed,
  *                          in declaration order.
  * @param partitionKey      partition-key column names, in key order.
  * @param clusteringColumns clustering columns with their sort direction, in
  *                          declaration order.
  * @param ifNotExistsFlag   whether to emit `IF NOT EXISTS` (set via [[ifNotExists]]).
  */
case class CreateTable(
    table: TableDef,
    columns: Seq[TableDef#Column[_]],
    partitionKey: Seq[String],
    clusteringColumns: Seq[ClusteringSpec],
    ifNotExistsFlag: Boolean = false
) {

  /** Emit `CREATE TABLE IF NOT EXISTS`, making the statement a no-op when the
    * table already exists instead of failing.
    */
  def ifNotExists: CreateTable = copy(ifNotExistsFlag = true)

  /** Run this statement synchronously and return the driver [[ResultSet]]. */
  def execute()(implicit session: CqlSession): ResultSet =
    session.execute(toCQL)

  /** Run this statement asynchronously, completing with the [[AsyncResultSet]]. */
  def executeAsync()(
      implicit session: CqlSession,
      @unused ec: ExecutionContext
  ): Future[AsyncResultSet] =
    session.executeAsync(toCQL).asScala

  /** Run this statement reactively, returning a [[ReactiveResultSet]]. */
  def executeReactive()(implicit session: CqlSession): ReactiveResultSet =
    session.executeReactive(toCQL)

  /** Render this statement as a CQL string (also what [[toString]] returns). */
  def toCQL: String = {
    val ifNotExistsStr = if (ifNotExistsFlag) " IF NOT EXISTS" else ""

    val columnDefs = columns
      .map(col => s"${col.name} ${col.codec.getCqlType.asCql(col.frozen, false)}")
      .mkString(", ")

    val pkCols      = partitionKey
    val clusterCols = clusteringColumns.map(_.name)

    val primaryKey =
      if (clusterCols.isEmpty && pkCols.size == 1)
        s"PRIMARY KEY (${pkCols.head})"
      else if (clusterCols.isEmpty)
        s"PRIMARY KEY ((${pkCols.mkString(", ")}))"
      else if (pkCols.size == 1)
        s"PRIMARY KEY (${pkCols.head}, ${clusterCols.mkString(", ")})"
      else
        s"PRIMARY KEY ((${pkCols.mkString(", ")}), ${clusterCols.mkString(", ")})"

    // The clustering order comes from the table's CK declaration (Asc / Desc
    // markers). ASC is the CQL default, so the clause is only emitted when at
    // least one column deviates from it.
    val withClause =
      if (!clusteringColumns.exists(_.descending)) ""
      else {
        val orderStr = clusteringColumns
          .map(c => s"${c.name} ${c.direction}")
          .mkString(", ")
        s" WITH CLUSTERING ORDER BY ($orderStr)"
      }

    s"CREATE TABLE$ifNotExistsStr ${table.fullTableName} ($columnDefs, $primaryKey)$withClause"
  }

  override def toString: String = toCQL
}
