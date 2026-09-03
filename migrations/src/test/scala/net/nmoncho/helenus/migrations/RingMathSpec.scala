/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.math.BigInteger
import java.nio.ByteBuffer

import com.datastax.oss.driver.internal.core.metadata.token.ByteOrderedToken
import com.datastax.oss.driver.internal.core.metadata.token.ByteOrderedTokenRange
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange
import com.datastax.oss.driver.internal.core.metadata.token.RandomToken
import com.datastax.oss.driver.internal.core.metadata.token.RandomTokenRange
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The per-partitioner weight math on synthetic ranges, so Random and
  * ByteOrdered are covered even though the embedded cluster is Murmur3.
  */
class RingMathSpec extends AnyWordSpec with Matchers {

  "RingMath" should {

    "measure a Murmur3 range as its exact ring fraction" in {
      // (0, 2^62] over a 2^64 ring is one quarter of the ring.
      val quarter =
        new Murmur3TokenRange(new Murmur3Token(0L), new Murmur3Token(1L << 62))

      RingMath.forRanges(Vector(quarter)).weight(quarter).toDouble shouldBe (0.25 +- 1e-9)
    }

    "measure the Murmur3 whole ring as approximately one" in {
      val whole =
        new Murmur3TokenRange(new Murmur3Token(Long.MinValue), new Murmur3Token(Long.MinValue))

      RingMath.forRanges(Vector(whole)).weight(whole).toDouble shouldBe (1.0 +- 1e-9)
    }

    "measure a Random range as its exact ring fraction" in {
      // (0, 2^126] over a 2^127 ring is one half of the ring.
      val half =
        new RandomTokenRange(
          new RandomToken(BigInteger.ZERO),
          new RandomToken(BigInteger.TWO.pow(126))
        )

      RingMath.forRanges(Vector(half)).weight(half).toDouble shouldBe (0.5 +- 1e-9)
    }

    "fall back to uniform weighting for a partitioner it cannot measure" in {
      def token(b: Byte): ByteOrderedToken = new ByteOrderedToken(ByteBuffer.wrap(Array(b)))

      val ranges: Vector[ByteOrderedTokenRange] = Vector(
        new ByteOrderedTokenRange(token(0), token(1)),
        new ByteOrderedTokenRange(token(1), token(2)),
        new ByteOrderedTokenRange(token(2), token(3)),
        new ByteOrderedTokenRange(token(3), token(4))
      )

      val math = RingMath.forRanges(
        ranges.map(r => r: com.datastax.oss.driver.api.core.metadata.token.TokenRange)
      )
      ranges.foreach(r => math.weight(r).toDouble shouldBe (0.25 +- 1e-9))
    }
  }
}
