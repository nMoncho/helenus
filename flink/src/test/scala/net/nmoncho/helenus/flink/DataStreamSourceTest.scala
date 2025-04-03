/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.flink.models.Hotel
import net.nmoncho.helenus.flink.source.CassandraSource
import net.nmoncho.helenus.utils.HotelsTestData
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DataStreamSourceTest extends AnyWordSpec with Matchers with FlinkCassandraSpec {

  "A ScalaPreparedStatement" should {
    "be used as a source" in {
      val env = StreamExecutionEnvironment.getExecutionEnvironment
        .setParallelism(2)

      val query =
        (session: CqlSession) => "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

      val input: DataStream[Hotel] = env.fromSource(
        query.asSource(
          CassandraSource
            .Config()
            .copy(config = cassandraConfig)
        ),
        WatermarkStrategy.noWatermarks(),
        "Cassandra Source"
      )

      val sink = input.print()

      env.execute()
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    HotelsTestData.insertTestData()(session)
  }

  override def afterEach(): Unit = () // don't truncate keyspace

}
