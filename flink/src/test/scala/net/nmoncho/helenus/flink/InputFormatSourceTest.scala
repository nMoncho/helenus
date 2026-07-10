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
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InputFormatSourceTest extends AnyFlatSpec with Matchers with FlinkCassandraSpec {

  "A ScalaPreparedStatement" should "be used as an InputFormat source" in {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
      .setParallelism(2)

    val query =
      (session: CqlSession) => "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

    val input: DataStreamSource[Hotel] = env.createDataSource(
      query.asInputFormat(
        CassandraSource
          .Config()
          .copy(config = cassandraConfig)
      )
    )

    val sink = input.print("Cassandra Sink")

    env.execute()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    HotelsTestData.insertTestData()(session)
  }
}
