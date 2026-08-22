/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class TokenRangePlannerSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaCheckDrivenPropertyChecks {

  private implicit def implicitSession: CqlSession = session

  // Building a plan hits the cluster metadata, so keep the sample count modest.
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 12)

  /** True when the plan's ranges tile the ring: sorted, each end meets the next
    * start and the last wraps back to the first, so there are no gaps or overlaps.
    */
  private def tilesTheRing(plan: RingPlan): Boolean = {
    val ranges   = plan.splits.map(_.range).sortWith((a, b) => a.compareTo(b) < 0)
    val adjacent = ranges.sliding(2).forall {
      case Seq(a, b) => a.getEnd == b.getStart
      case _ => true
    }

    adjacent && ranges.last.getEnd == ranges.head.getStart
  }

  "TokenRangePlanner" should {

    "produce a non-empty plan whose ranges cover the ring exactly once" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      plan.isEmpty shouldBe false

      // Sorted ranges must tile the ring: each range's end is the next range's
      // start, and the last range wraps back to the first. That proves the ranges
      // neither overlap nor leave gaps.
      val ranges = plan.splits.map(_.range).sortWith((a, b) => a.compareTo(b) < 0)

      ranges.sliding(2).foreach {
        case Seq(a, b) => a.getEnd shouldBe b.getStart
        case _ => ()
      }
      ranges.last.getEnd shouldBe ranges.head.getStart
    }

    "assign weights that sum to approximately one" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      plan.totalWeight.toDouble shouldBe (1.0 +- 0.0001)
      plan.splits.foreach(_.weight.toDouble should be > 0.0)
    }

    "resolve a replica for every range when a keyspace is set" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 4)

      plan.splits.foreach(_.replica should not be empty)
      plan.byReplica.keySet should not contain None
    }

    "produce more ranges as the split factor grows" in {
      TokenRangePlanner.plan(splitsPerRange = 8).size should be >=
        TokenRangePlanner.plan(splitsPerRange = 1).size
    }

    "treat a non-positive split factor as one" in {
      TokenRangePlanner.plan(splitsPerRange = 0).isEmpty shouldBe false
    }

    "fall back to a single whole-ring split" in {
      val plan = TokenRangePlanner.wholeRing

      plan.size shouldBe 1
      plan.splits.head.weight shouldBe BigDecimal(1)
      plan.splits.head.range shouldBe a[TokenRange]
    }

    "cover the ring exactly once for any split factor (property)" in {
      forAll(Gen.choose(1, 16)) { splitsPerRange =>
        val plan = TokenRangePlanner.plan(splitsPerRange)

        plan.isEmpty shouldBe false
        tilesTheRing(plan) shouldBe true
        plan.totalWeight.toDouble shouldBe (1.0 +- 0.001)
        plan.splits.foreach(_.replica should not be empty)
      }
    }
  }
}
