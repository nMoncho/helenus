/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import java.time.Duration

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
  * or when clustering columns follow), a `CLUSTERING ORDER BY` clause only when
  * at least one clustering column is declared descending (ASC being the CQL
  * default), and then any table [[options]] set through the fluent `with*`
  * methods. `CLUSTERING ORDER BY` and every option share one `WITH` clause,
  * joined with ` AND `.
  *
  * {{{
  * UsersTable.create
  *   .ifNotExists
  *   .withComment("users")
  *   .withGcGraceSeconds(864000)
  *   .withCompaction(Map("class" -> "LeveledCompactionStrategy"))
  *   .execute()
  * }}}
  *
  * @param table             the table this statement creates.
  * @param columns           every column to declare, registered plus computed,
  *                          in declaration order.
  * @param partitionKey      partition-key column names, in key order.
  * @param clusteringColumns clustering columns with their sort direction, in
  *                          declaration order.
  * @param ifNotExistsFlag   whether to emit `IF NOT EXISTS` (set via [[ifNotExists]]).
  * @param options           the table's `WITH` options (see [[TableOptions]]),
  *                          set through the fluent `with*` methods.
  */
case class CreateTable(
    table: TableDef,
    columns: Seq[TableDef#Column[_]],
    partitionKey: Seq[String],
    clusteringColumns: Seq[ClusteringSpec],
    ifNotExistsFlag: Boolean = false,
    options: TableOptions    = TableOptions.Empty
) {

  /** Emit `CREATE TABLE IF NOT EXISTS`, making the statement a no-op when the
    * table already exists instead of failing.
    */
  def ifNotExists: CreateTable = copy(ifNotExistsFlag = true)

  // ---- table options (WITH ...): each returns a copy, so they chain ---------

  /** Replace all table options at once (see [[TableOptions]]). */
  def withOptions(options: TableOptions): CreateTable = copy(options = options)

  /** A human-readable comment stored with the table. */
  def withComment(comment: String): CreateTable =
    copy(options = options.copy(comment = Some(comment)))

  /** When to speculatively retry reads, e.g. `99percentile`, `50ms`, `ALWAYS`, `NONE`. */
  def withSpeculativeRetry(value: TableOptions.SpeculativeRetry): CreateTable =
    copy(options = options.copy(speculativeRetry = Some(value)))

  /** Speculative-retry policy applied to writes (Cassandra 4.0+). */
  def withAdditionalWritePolicy(value: TableOptions.SpeculativeRetry): CreateTable =
    copy(options = options.copy(additionalWritePolicy = Some(value)))

  /** Seconds tombstones are retained before becoming eligible for GC. */
  def withGcGraceSeconds(seconds: Duration): CreateTable =
    copy(options = options.copy(gcGraceSeconds = Some(seconds)))

  /** Target false-positive probability for the SSTable bloom filters. */
  def withBloomFilterFpChance(chance: Double): CreateTable =
    copy(options = options.copy(bloomFilterFpChance = Some(chance)))

  /** Default TTL (seconds) for inserted data; `0` disables it. */
  def withDefaultTimeToLive(seconds: Duration): CreateTable =
    copy(options = options.copy(defaultTimeToLive = Some(seconds)))

  /** Forced memtable flush period in milliseconds; `0` disables it. */
  def withMemtableFlushPeriodInMs(millis: Duration): CreateTable =
    copy(options = options.copy(memtableFlushPeriodInMs = Some(millis)))

  /** Minimum sampling interval for the partition index. */
  def withMinIndexInterval(value: Int): CreateTable =
    copy(options = options.copy(minIndexInterval = Some(value)))

  /** Maximum sampling interval for the partition index. */
  def withMaxIndexInterval(value: Int): CreateTable =
    copy(options = options.copy(maxIndexInterval = Some(value)))

  /** Read-repair behaviour: `BLOCKING` (default) or `NONE` (Cassandra 4.0+).
    * See <a href="https://cassandra.apache.org/doc/stable/cassandra/managing/operating/read_repair.html">Read repair</a>
    */
  def withReadRepair(value: TableOptions.ReadRepair): CreateTable =
    copy(options = options.copy(readRepair = Some(value)))

  /** Probability of verifying SSTable checksums on read. */
  def withCrcCheckChance(chance: Double): CreateTable =
    copy(options = options.copy(crcCheckChance = Some(chance)))

  /** Enable or disable change-data-capture for the table. */
  def withCdc(enabled: Boolean): CreateTable =
    copy(options = options.copy(cdc = Some(enabled)))

  /** Caching options, e.g. `Map("keys" -> "ALL", "rows_per_partition" -> "NONE")`. */
  def withCaching(caching: TableOptions.Caching): CreateTable =
    copy(options = options.copy(caching = Some(caching)))

  /** Compaction options, e.g. `Map("class" -> "LeveledCompactionStrategy")`. */
  def withCompaction(compaction: TableOptions.CompactionStrategy): CreateTable =
    copy(options = options.copy(compaction = Some(compaction)))

  /** Compression options, e.g. `Map("class" -> "LZ4Compressor")`. */
  def withCompression(compression: TableOptions.Compression): CreateTable =
    copy(options = options.copy(compression = Some(compression)))

  /** Named memtable configuration (Cassandra 4.1+). */
  def withMemtable(name: String): CreateTable =
    copy(options = options.copy(memtable = Some(name)))

  /** Explicit table id (a UUID string), e.g. to recreate a dropped table. */
  def withId(id: String): CreateTable =
    copy(options = options.copy(id = Some(id)))

  /** Escape hatch for any option without a typed method (DSE-only options, or
    * ones added by a newer server). Rendered verbatim as `name = value`, so
    * supply CQL-ready syntax (a string value must already be quoted, e.g.
    * `withOption("nodesync", "{'enabled': 'true'}")`).
    */
  def withOption(name: String, value: String): CreateTable =
    copy(options = options.copy(extra = options.extra :+ (name -> value)))

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
      .map(_.toCQL)
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
    // least one column deviates from it. It leads the WITH clause, before any
    // table option.
    val clusteringOrder: Option[String] =
      if (!clusteringColumns.exists(_.descending)) None
      else {
        val orderStr = clusteringColumns
          .map(c => s"${c.name} ${c.direction}")
          .mkString(", ")
        Some(s"CLUSTERING ORDER BY ($orderStr)")
      }

    val withElements = clusteringOrder.toSeq ++ options.render
    val withClause   =
      if (withElements.isEmpty) "" else s" WITH ${withElements.mkString(" AND ")}"

    s"CREATE TABLE$ifNotExistsStr ${table.fullTableName} ($columnDefs, $primaryKey)$withClause"
  }

  override def toString: String = toCQL
}
