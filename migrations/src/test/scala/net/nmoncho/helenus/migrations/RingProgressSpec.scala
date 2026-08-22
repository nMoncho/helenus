/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** H-style unit tests for the pure progress math, with no cluster. */
class RingProgressSpec extends AnyWordSpec with Matchers {

  private val range = new Murmur3TokenRange(new Murmur3Token(0L), new Murmur3Token(1L << 62))

  private def split(weight: Double): RangeSplit = RangeSplit(range, None, BigDecimal(weight))

  "RingProgress" should {

    "start at zero and reach one as ranges complete" in {
      val (a, b, c) = (split(0.25), split(0.25), split(0.5))
      val plan      = RingPlan(Vector(a, b, c))

      val start = RingProgress(plan)
      start.fraction shouldBe (0.0 +- 1e-9)

      start.completing(a).fraction shouldBe (0.25 +- 1e-9)
      start.completing(a).completing(b).completing(c).fraction shouldBe (1.0 +- 1e-9)
    }

    "expose per-replica fractions" in {
      val (a, b) = (split(0.4), split(0.6))
      val plan   = RingPlan(Vector(a, b))

      RingProgress(plan)
        .completing(a)
        .fractionByReplica(RingProgress.Unassigned) shouldBe (0.4 +- 1e-9)
    }

    "treat an empty plan as complete" in {
      RingProgress(RingPlan(Vector.empty)).fraction shouldBe 1.0
    }

    "cap the fraction at one even if over-completed" in {
      val a    = split(1.0)
      val plan = RingPlan(Vector(a))

      RingProgress(plan).completing(a).completing(a).fraction shouldBe (1.0 +- 1e-9)
    }
  }
}
