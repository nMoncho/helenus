/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.sink

import scala.util.control.NonFatal

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BatchStatement
import com.datastax.oss.driver.api.core.cql.DefaultBatchType
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import org.apache.spark.rdd.RDD

/** The CQL-first `foreachPartition` write path.
  *
  * This is the primary, recommended typed write path on Spark. Unlike the connector's
  * `saveToCassandra` — which maps a domain object to a single INSERT — it runs a
  * user-provided `CqlSession => ScalaPreparedStatement[In, Out]` and binds each record
  * with compile-time bind-arity safety, so it can express what the column-mapping model
  * cannot: LWT / conditional writes (`IF NOT EXISTS`, `IF ...`), custom-`WHERE` updates and
  * deletes, and arbitrary CQL.
  */
object CassandraSink {

  /** Tuning for the CQL-first sink.
    *
    * Unlike the Flink `CassandraSink.Config`, this carries no Typesafe `config` /
    * `configPath` and no `session()` builder: on Spark the session is the connector's,
    * driven by the `spark.cassandra.*` keys in the `SparkConf`, so this `Config`
    * only tunes the Helenus sink layered on top and never duplicates connector keys. It
    * also drops Flink's `maxConcurrentRequests` / `maxConcurrentRequestsTimeout`, which
    * only make sense for the async throttling the Flink sink implements and this
    * synchronous sink does not.
    *
    * ==At-least-once semantics==
    *
    * Spark re-executes a failed task, re-running the '''whole''' partition, and this
    * CQL-first path bypasses the connector's `WriteConf` (it executes user statements
    * directly through the yielded session), so its idempotency and consistency options do
    * not apply here. A partition's statements may therefore be applied more than once. Keep
    * them idempotent — plain inserts/upserts and `IF NOT EXISTS` are safe to replay — and
    * for statements that are '''not''' (counter updates, non-idempotent conditional writes)
    * set `idempotent = false` and design for the possibility of a replay.
    *
    * @param batchSize      how many bound statements to group into one `UNLOGGED` batch per
    *                       execute. `1` (the default) executes each record on its own,
    *                       which is what LWT / conditional writes require — those cannot be
    *                       batched, and a multi-partition batch is a Cassandra anti-pattern,
    *                       so raise this only for many small same-partition writes.
    * @param idempotent     applied to every statement via `setIdempotent`; `true` (the
    *                       default) lets the driver retry them and matches the safe-to-replay
    *                       majority (upserts, `IF NOT EXISTS`). Set `false` for counters or
    *                       non-idempotent conditional writes, which must not be replayed by
    *                       the driver's retry policy.
    * @param failureHandler invoked with a write failure before it is rethrown, so a failing
    *                       partition surfaces through the handler rather than being lost;
    *                       failures are never silently swallowed.
    */
  final case class Config(
      batchSize: Int                    = Config.DefaultBatchSize,
      idempotent: Boolean               = Config.DefaultIdempotent,
      failureHandler: Throwable => Unit = Config.NoOpFailureHandler
  ) {
    require(batchSize > 0, "batchSize is expected to be positive")
  }

  object Config {
    val DefaultBatchSize: Int                 = 1
    val DefaultIdempotent: Boolean            = true
    val NoOpFailureHandler: Throwable => Unit = _ => ()
  }

  /** Runs `builder` once per partition inside the connector's session and executes a bound
    * statement per record (or per `config.batchSize` group).
    *
    * The statement is prepared lazily on the executor — only the small `builder` function
    * crosses the wire, exactly as the Flink sink does — so a non-serializable capture in
    * `builder` surfaces as a real cross-executor failure rather than working by accident on
    * a local run. The `SparkConf` is read on the driver (it is serializable; a
    * `SparkContext` is not) and the connector is rebuilt per partition.
    */
  private[spark] def write[In, Out](
      rdd: RDD[In],
      builder: CqlSession => ScalaPreparedStatement[In, Out],
      config: Config
  ): Unit = {
    val conf = rdd.sparkContext.getConf

    rdd.foreachPartition { rows =>
      CassandraSessions.withSession(conf) { session =>
        // Prepared once per partition on the executor, then reused for every record.
        val pstmt = builder(session)

        rows.grouped(config.batchSize).foreach { chunk =>
          try {
            chunk match {
              case Seq(single) =>
                session.execute(pstmt.tupled(single).setIdempotent(config.idempotent))

              case many =>
                val batch = BatchStatement.builder(DefaultBatchType.UNLOGGED)
                many.foreach(record => batch.addStatement(pstmt.tupled(record)))
                session.execute(batch.build().setIdempotent(config.idempotent))
            }
            ()
          } catch {
            case NonFatal(t) =>
              config.failureHandler(t)
              throw t
          }
        }
      }
    }
  }
}
