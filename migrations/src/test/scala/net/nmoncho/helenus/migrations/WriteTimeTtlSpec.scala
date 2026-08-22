/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Pure unit tests for the row-level WRITETIME/TTL approximation (no cluster). */
class WriteTimeTtlSpec extends AnyWordSpec with Matchers {

  "WriteTimeTtl.rowLevel" should {

    "take the max writetime and the smallest positive TTL" in {
      WriteTimeTtl.rowLevel(Seq(10L, 30L, 20L), Seq(Some(100), None, Some(50))) shouldBe
        WriteTimeTtl(30L, Some(50))
    }

    "yield no TTL when no cell has a positive TTL" in {
      WriteTimeTtl.rowLevel(Seq(5L), Seq(None, Some(0), Some(-1))) shouldBe WriteTimeTtl(5L, None)
    }

    "require at least one writetime" in {
      an[IllegalArgumentException] should be thrownBy WriteTimeTtl.rowLevel(Nil, Seq(Some(10)))
    }
  }

  "WriteTimeTtl.ttlOrZero" should {
    "bind 0 for no expiry and the value otherwise" in {
      WriteTimeTtl(1L, None).ttlOrZero shouldBe 0
      WriteTimeTtl(1L, Some(7)).ttlOrZero shouldBe 7
    }
  }
}
