/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

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
  * A Cassandra to Cassandra migration on Flink reads with `asSource` and writes with
  * `addCassandraSink`, both from `net.nmoncho.helenus.flink`:
  *
  * {{{
  * import net.nmoncho.helenus._
  * import net.nmoncho.helenus.flink._
  *
  * val read = (s: CqlSession) => "SELECT id, v FROM source".toCQL(s).prepareUnit.as[Migrated].apply()
  *
  * env
  *   .fromSource(read.asSource(sourceConfig), WatermarkStrategy.noWatermarks(), "cassandra-source")
  *   .addCassandraSink(
  *     (s: CqlSession) => "INSERT INTO target (id, v) VALUES (?, ?)".toCQL(s).prepare[Int, String].from[Migrated],
  *     sinkConfig
  *   )
  *
  * env.execute()
  * }}}
  */
package object flink
