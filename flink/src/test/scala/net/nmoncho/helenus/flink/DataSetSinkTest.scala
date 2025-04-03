/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink

import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.flink.sink.CassandraSink
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.HotelsTestData.Hotels
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.java.DataSet
import org.apache.flink.api.java.ExecutionEnvironment
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataSetSinkTest extends AnyFlatSpec with Matchers with FlinkCassandraSpec {

  "A ScalaPreparedStatement" should "work as a DataSink for a DataSet" in {
    val query = "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel]
    query.execute()(session).to(List) shouldBe empty

    val job = ExecutionEnvironment.getExecutionEnvironment

    job.setParallelism(2)

    val input: DataSet[Hotel] = job.fromElements(Hotels.all: _*)

    val result: DataSet[(String, String, String, Address, Set[String])] =
      input.map(new MapFunction[Hotel, (String, String, String, Address, Set[String])] {
        override def map(h: Hotel): (String, String, String, Address, Set[String]) =
          (h.id, h.name, h.phone, h.address, h.pois)
      })

    result
      .addCassandraOutput(
        """INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)""".stripMargin
          .toCQL(_)
          .prepare[String, String, String, Address, Set[String]],
        CassandraSink
          .Config()
          .copy(config = cassandraConfig)
      )
      .setParallelism(1)

    job.execute()

    query.execute()(session).to(List) should not be empty
  }

  it should "work with an adapter" in {
    val query = "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel]
    query.execute()(session).to(List) shouldBe empty

    val job = ExecutionEnvironment.getExecutionEnvironment

    job.setParallelism(2)

    val input: DataSet[Hotel] = job.fromElements(Hotels.all: _*)

    implicit val adapter: Adapter[Hotel, (String, String, String, Address, Set[String])] =
      Adapter[Hotel]

    input
      .addCassandraOutput(
        """INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)""".stripMargin
          .toCQL(_)
          .prepare[String, String, String, Address, Set[String]]
          .from[Hotel],
        CassandraSink
          .Config()
          .copy(config = cassandraConfig)
      )
      .setParallelism(1)

    job.execute()

    query.execute()(session).to(List) should not be empty
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
  }
}
