/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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
