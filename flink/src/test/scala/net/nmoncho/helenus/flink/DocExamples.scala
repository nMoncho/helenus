/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.flink.models.Address
import net.nmoncho.helenus.flink.models.Hotel
import net.nmoncho.helenus.flink.sink.CassandraSink
import net.nmoncho.helenus.flink.source.CassandraSource
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

/** Compile-checked usage examples backing this module's README.
  *
  * The bodies mirror the README snippets; they are never executed, so they need no live Cassandra
  * connection or a running Flink job. Keeping them here means the README examples cannot silently
  * stop compiling. In real code, populate `CassandraSource.Config()` / `CassandraSink.Config()`
  * with your driver configuration.
  */
object DocExamples {

  /** A prepared `SELECT` becomes a Flink `Source`. */
  def source(env: StreamExecutionEnvironment): DataStream[Hotel] = {
    val query =
      (session: CqlSession) => "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

    env.fromSource(
      query.asSource(CassandraSource.Config()),
      WatermarkStrategy.noWatermarks(),
      "Cassandra Source"
    )
  }

  /** A `DataStream` is written to Cassandra through a prepared `INSERT`. */
  def sink(hotels: DataStream[Hotel]): Unit = {
    val rows: DataStream[(String, String, String, Address)] =
      hotels.map(new MapFunction[Hotel, (String, String, String, Address)] {
        override def map(h: Hotel): (String, String, String, Address) =
          (h.id, h.name, h.phone, h.address)
      })

    rows.addCassandraSink(
      "INSERT INTO hotels(id, name, phone, address) VALUES (?, ?, ?, ?)".toCQL(_)
        .prepare[String, String, String, Address],
      CassandraSink.Config()
    )

    ()
  }
}
