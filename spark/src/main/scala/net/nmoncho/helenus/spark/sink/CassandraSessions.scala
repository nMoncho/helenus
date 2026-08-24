/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.sink

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.SparkConf

/** Single entry point for borrowing a [[CqlSession]] on the Spark paths.
  *
  * The Spark module never builds its own session with the driver's session builder;
  * it always borrows the connector's session through
  * [[CassandraConnector.withSessionDo]]. That way it inherits the connector's
  * pooling, retry, and topology handling and never opens a competing session per
  * partition, which at scale could exhaust connections.
  *
  * This is the opposite of the Flink module, which has no session-owning connector
  * and therefore must build its own [[CqlSession]]. On Spark the connector owns the
  * session, driven by the `spark.cassandra.*` keys in the `SparkConf`.
  */
private[spark] object CassandraSessions {

  /** Runs `code` with a session borrowed from the connector built for `conf`.
    *
    * This is the executor-side path: a `SparkConf` is serializable and reaches
    * executors, so a task can rebuild the connector and borrow a session there,
    * whereas a `SparkContext` cannot cross the wire.
    */
  def withSession[T](conf: SparkConf)(code: CqlSession => T): T =
    CassandraConnector(conf).withSessionDo(code)

  /** Runs `code` with a session borrowed from an already-built `connector`.
    *
    * `CassandraConnector` is itself serializable, so a connector captured on the
    * driver can be shipped to executors and used here.
    */
  def withSession[T](connector: CassandraConnector)(code: CqlSession => T): T =
    connector.withSessionDo(code)
}
