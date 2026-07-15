/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package ddl

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps

case class CreateTable(
    table: TableDef,
    columns: Seq[TableDef#Column[_]],
    partitionKey: Seq[String],
    clusteringColumns: Seq[ClusteringSpec],
    ifNotExistsFlag: Boolean = false
) {

  def ifNotExists: CreateTable = copy(ifNotExistsFlag = true)

  def execute()(implicit session: CqlSession): ResultSet =
    session.execute(toCQL)

  def executeAsync()(
      implicit session: CqlSession,
      @unused ec: ExecutionContext
  ): Future[AsyncResultSet] =
    session.executeAsync(toCQL).asScala

  def executeReactive()(implicit session: CqlSession): ReactiveResultSet =
    session.executeReactive(toCQL)

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
