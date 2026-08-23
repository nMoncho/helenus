/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example

import scala.jdk.CollectionConverters._

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** C4: the example `reference.conf` documents every knob, and `Config.fromConfig`
  * reads them into the same parameters the executor takes.
  */
class MigrationConfigSpec extends AnyWordSpec with Matchers {

  private val config = ConfigFactory.parseResources("helenus-migration-example.conf").resolve()

  "The example configuration" should {

    "map every migration knob through Config.fromConfig" in {
      val migration = MigrationApp.Config.fromConfig(config.getConfig("helenus-migration"))

      migration.splits shouldBe 8
      migration.parallelism shouldBe 8
      migration.rateLimit.map(_.elements) shouldBe Some(1000)
      migration.rateLimit.map(_.per.toSeconds) shouldBe Some(1L)
      migration.executionProfile shouldBe Some("migration-read")
    }

    "document the liveness endpoint" in {
      val healthCheck = config.getConfig("helenus-migration.health-check")

      healthCheck.getString("host") shouldBe "0.0.0.0"
      healthCheck.getInt("port") shouldBe 8080
      healthCheck.getString("url") shouldBe "health"
    }

    "document the driver contact points and the read profile" in {
      config
        .getStringList("datastax-java-driver.basic.contact-points")
        .asScala should contain("localhost:9042")

      config
        .getString("datastax-java-driver.profiles.migration-read.basic.request.consistency") shouldBe
        "LOCAL_ONE"
    }
  }

  "Config.fromConfig" should {

    "leave the rate limit and profile unset when they are absent" in {
      val minimal = MigrationApp.Config.fromConfig(
        ConfigFactory.parseString("token-range-splits = 4")
      )

      minimal.splits shouldBe 4
      minimal.rateLimit shouldBe None
      minimal.executionProfile shouldBe None
    }
  }
}
