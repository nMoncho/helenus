/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import _root_.net.nmoncho.helenus.api.RowMapper
import _root_.net.nmoncho.helenus.api.cql.ScalaBoundStatement
import _root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import _root_.net.nmoncho.helenus.flink._
import com.datastax.oss.driver.api.core.CqlSession
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

/** Flink executor for a token-range full-table scan.
  *
  * The token-range Flink source is already provided by `helenus-flink`, so the
  * migrations module does not reimplement it. `net.nmoncho.helenus.flink`'s
  * `CassandraSource` is a FLIP-27 bounded source whose splits are ring slices, whose
  * reader adds a `token(pk) >= ? AND token(pk) < ?` predicate per split, and whose
  * split-enumerator state is checkpointed. Flink therefore distributes ranges across
  * subtasks and a savepoint resumes without re-reading finished splits, natively and
  * cluster-wide. That is why Flink is the recommended executor when native
  * checkpointing and cluster-level scale matter.
  *
  * [[asTokenRangeMigration]] wires a whole Cassandra to Cassandra migration onto a
  * `StreamExecutionEnvironment` in one call; the caller then runs `env.execute()`.
  * For per-row observability use Flink's own metrics system rather than
  * [[MigrationMetrics]] (the Flink operators do not surface those callbacks).
  */
package object flink {

  /** Wires a Cassandra to Cassandra migration onto `env`: read the source table with
    * the token-range [[CassandraSource]], map each row with `transform`, and write the
    * result with the Cassandra sink. Returns the sink so the caller can tune its
    * parallelism; call `env.execute()` to run it.
    *
    * The builders and `transform` become part of the serialized Flink job graph, so
    * they must be serializable: do not capture the enclosing instance (bind table
    * names to locals), and provide a hand-written [[RowMapper]] rather than a derived
    * one, because the core codecs an auto-derived mapper captures are not Serializable.
    *
    * @param env          the Flink stream environment to wire the job onto
    * @param read         builds the source `SELECT ... FROM table` bound statement
    * @param transform    turns each read row into a row to write
    * @param write        builds the target `INSERT ...` prepared statement
    * @param sourceConfig driver config for the source (contact points, keyspace, ...)
    * @param sinkConfig   driver config for the sink
    * @param sourceName   Flink operator name for the source
    */
  def asTokenRangeMigration[In, Out, W](
      env: StreamExecutionEnvironment,
      read: CqlSession => ScalaBoundStatement[In],
      transform: In => Out,
      write: CqlSession => ScalaPreparedStatement[Out, W],
      sourceConfig: CassandraSource.Config = CassandraSource.Config(),
      sinkConfig: CassandraSink.Config     = CassandraSink.Config(),
      sourceName: String                   = "cassandra-token-range-source"
  )(
      implicit readMapper: RowMapper[In],
      readTypeInfo: TypeInformation[In],
      writeTypeInfo: TypeInformation[Out]
  ): CassandraSink[Out] = {
    val extracted =
      env.fromSource(read.asSource(sourceConfig), WatermarkStrategy.noWatermarks(), sourceName)

    val transformed = extracted.map(
      new MapFunction[In, Out] {
        override def map(value: In): Out = transform(value)
      },
      writeTypeInfo
    )

    transformed.addCassandraSink(write, sinkConfig)
  }
}
