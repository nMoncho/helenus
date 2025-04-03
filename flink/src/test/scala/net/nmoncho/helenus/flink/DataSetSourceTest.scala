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
import org.apache.flink.api.java.ExecutionEnvironment
import org.apache.flink.api.java.operators.DataSource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataSetSourceTest extends AnyFlatSpec with Matchers with FlinkCassandraSpec {

  "A ScalaPreparedStatement" should "be used as a source" in {
    val job = ExecutionEnvironment.getExecutionEnvironment

    job.setParallelism(2)

    val query =
      (session: CqlSession) => "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

    val input: DataSource[Hotel] = job.createDataSource(
      query.asInputFormat(
        CassandraSource
          .Config()
          .copy(config = cassandraConfig)
      )
    )

    val sink = input.print("Cassandra Sink")

    job.execute()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    HotelsTestData.insertTestData()(session)
  }
}
