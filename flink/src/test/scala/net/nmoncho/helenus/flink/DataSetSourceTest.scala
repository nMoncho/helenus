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
