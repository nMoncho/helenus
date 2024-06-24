/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus
package flink

import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.flink.sink.CassandraSink
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.HotelsTestData.Hotels
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DataStreamSinkTest extends AnyFlatSpec with Matchers with FlinkCassandraSpec {

  "A ScalaPreparedStatement" should "work as a SinkFunction for a DataStream" in {
    val query = "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel]
    query.execute()(session).to(List) shouldBe empty

    val env = StreamExecutionEnvironment.getExecutionEnvironment
      .setParallelism(2)

    val input: DataStream[Hotel] = env.fromElements(Hotels.all: _*)

    val result: DataStream[(String, String, String, Address)] =
      input.map(new MapFunction[Hotel, (String, String, String, Address)] {
        override def map(h: Hotel): (String, String, String, Address) =
          (h.id, h.name, h.phone, h.address)
      })

    result
      .addCassandraSink(
        "INSERT INTO hotels(id, name, phone, address) VALUES (?, ?, ?, ?)"
          .toCQL(_)
          .prepare[String, String, String, Address],
        CassandraSink
          .Config()
          .copy(config = cassandraConfig)
      )
      .setParallelism(1)

    env.execute()

    query.execute()(session).to(List) should not be empty
  }

  it should "work with an adapter" in {
    val query = "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel]
    query.execute()(session).to(List) shouldBe empty

    val env = StreamExecutionEnvironment.getExecutionEnvironment
      .setParallelism(2)

    val input: DataStream[Hotel] = env.fromElements(Hotels.all: _*)

    implicit val adapter: Adapter[Hotel, (String, String, String, Address, Set[String])] =
      Adapter[Hotel]

    input
      .addCassandraSink(
        "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
          .toCQL(_)
          .prepare[String, String, String, Address, Set[String]]
          .from[Hotel],
        CassandraSink
          .Config()
          .copy(config = cassandraConfig)
      )
      .setParallelism(1)

    env.execute()

    query.execute()(session).to(List) should not be empty
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
  }
}
