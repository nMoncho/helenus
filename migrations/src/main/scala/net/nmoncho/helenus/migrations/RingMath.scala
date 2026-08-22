/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.RandomToken

/** Measures the fraction of the token ring a range covers, per partitioner.
  *
  * Murmur3 and Random are measured exactly from their numeric token values; any
  * other partitioner (for example ByteOrdered) falls back to uniform weighting, so
  * an unknown partitioner produces a usable plan instead of throwing. Kept
  * `private[migrations]` so it is unit-testable on synthetic ranges.
  */
private[migrations] sealed trait RingMath {
  def weight(range: TokenRange): BigDecimal
}

private[migrations] object RingMath {

  private val Murmur3RingSize: BigDecimal = BigDecimal(2).pow(64)
  private val Murmur3Max: BigDecimal      = BigDecimal(Long.MaxValue)
  private val RandomRingSize: BigDecimal  = BigDecimal(2).pow(127)

  /** Selects a measurement strategy from the plan's ranges, by the type of the
    * first range's start token. Uniform weighting is the fallback for anything we
    * cannot measure numerically.
    */
  def forRanges(ranges: Vector[TokenRange]): RingMath =
    ranges.headOption.map(_.getStart) match {
      case Some(_: Murmur3Token) => Murmur3
      case Some(_: RandomToken) => Random
      case _ => Uniform(ranges.size)
    }

  case object Murmur3 extends RingMath {
    def weight(range: TokenRange): BigDecimal = (range.getStart, range.getEnd) match {
      case (start: Murmur3Token, end: Murmur3Token) =>
        // The minimum token doubles as the ring's wrap sentinel, so an end at the
        // minimum means "up to the maximum token".
        val endValue =
          if (end.getValue == Long.MinValue) Murmur3Max else BigDecimal(end.getValue)

        ((endValue - BigDecimal(start.getValue)) / Murmur3RingSize).abs

      case _ => BigDecimal(0)
    }
  }

  case object Random extends RingMath {
    def weight(range: TokenRange): BigDecimal = (range.getStart, range.getEnd) match {
      case (start: RandomToken, end: RandomToken) =>
        val length   = BigDecimal(BigInt(end.getValue)) - BigDecimal(BigInt(start.getValue))
        val positive = if (length <= 0) length + RandomRingSize else length

        (positive / RandomRingSize).abs

      case _ => BigDecimal(0)
    }
  }

  final case class Uniform(count: Int) extends RingMath {
    private val each: BigDecimal = if (count <= 0) BigDecimal(0) else BigDecimal(1) / count

    def weight(range: TokenRange): BigDecimal = {
      val _ = range
      each
    }
  }
}
