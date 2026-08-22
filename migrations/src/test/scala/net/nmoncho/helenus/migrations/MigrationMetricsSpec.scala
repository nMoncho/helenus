/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import scala.collection.mutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B8: the progress and count utilities built on [[MigrationMetrics]]. Pure, no cluster. */
class MigrationMetricsSpec extends AnyWordSpec with Matchers {

  private def progress(fraction: Double): RingProgress =
    RingProgress(BigDecimal(1), BigDecimal(fraction), Map.empty, Map.empty)

  "MigrationMetrics.counting" should {

    "count extracted, loaded, ranges, and failures, and track peak progress" in {
      val metrics = MigrationMetrics.counting()

      metrics.rowExtracted()
      metrics.rowExtracted()
      metrics.rowLoaded()
      metrics.rangeCompleted(progress(0.5))
      metrics.rangeCompleted(progress(1.0))
      metrics.rangeFailed(new RuntimeException("boom"))

      metrics.extracted shouldBe 2L
      metrics.loaded shouldBe 1L
      metrics.rangesCompleted shouldBe 2L
      metrics.failed shouldBe 1L
      metrics.fraction shouldBe (1.0 +- 1e-9)
    }
  }

  "MigrationMetrics.logging" should {

    "log a line per progress step and per failure (logProgress replacement)" in {
      val logged  = mutable.Buffer.empty[String]
      val metrics = MigrationMetrics.logging("job", stepPercent = 10, log = logged.append(_))

      metrics.rangeCompleted(progress(0.1))
      metrics.rangeCompleted(progress(0.2))
      metrics.rangeCompleted(progress(1.0))
      metrics.rangeFailed(new RuntimeException("kaboom"))

      logged should have size 4
      logged.count(_.contains("progress")) shouldBe 3
      logged.last should include("kaboom")
    }

    "not log the same progress step twice" in {
      val logged  = mutable.Buffer.empty[String]
      val metrics = MigrationMetrics.logging("job", stepPercent = 10, log = logged.append(_))

      metrics.rangeCompleted(progress(0.11))
      metrics.rangeCompleted(progress(0.15)) // same 10% step, so not logged again

      logged should have size 1
    }
  }

  "MigrationMetrics.all" should {

    "fan every callback out to each delegate" in {
      val a   = MigrationMetrics.counting()
      val b   = MigrationMetrics.counting()
      val fan = MigrationMetrics.all(a, b)

      fan.rowExtracted()
      fan.rangeCompleted(progress(1.0))

      a.extracted shouldBe 1L
      b.extracted shouldBe 1L
      a.rangesCompleted shouldBe 1L
      b.rangesCompleted shouldBe 1L
    }
  }
}
