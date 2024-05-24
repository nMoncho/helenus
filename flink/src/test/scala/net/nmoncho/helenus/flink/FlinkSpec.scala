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

package net.nmoncho.helenus.flink

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite

trait FlinkSpec extends BeforeAndAfterEach { this: Suite =>

  protected def numberSlotsPerTaskManager: Int = 2

  protected def numberTaskManagers: Int = 1

  protected lazy val flinkCluster = new MiniClusterWithClientResource(
    new MiniClusterResourceConfiguration.Builder()
      .setNumberSlotsPerTaskManager(2)
      .setNumberTaskManagers(1)
      .build
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    flinkCluster.before()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    flinkCluster.after()
  }
}

trait FlinkCassandraSpec extends FlinkSpec with CassandraSpec { this: Suite =>

  protected def cassandraConfig: Config = ConfigFactory
    .parseString(s"""
         |datastax-java-driver.basic {
         |  contact-points = ["$contactPoint"]
         |  session-keyspace = "$keyspace"
         |  load-balancing-policy.local-datacenter = "datacenter1"
         |}""".stripMargin)
    .withFallback(ConfigFactory.load())

}
