/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token

/** Internal helpers shared by the per-backend executors. */
private[migrations] object TokenRing {

  private val Murmur3Max: Token = new Murmur3Token(Long.MaxValue)

  /** Normalizes the upper bound of a range for a bounded `token(pk) <= ?` query.
    *
    * The range whose end is the ring's minimum token is the wrap seam: it
    * represents "everything above its start, up to the maximum token". Binding
    * `token(pk) <= <minimum>` would match almost nothing and silently drop the top
    * slice of the ring, so the end is rewritten to the maximum token, making the
    * bounded query `token(pk) > start AND token(pk) <= maximum` correct.
    *
    * This is implemented for the default Murmur3 partitioner; other partitioners
    * pass through unchanged (a follow-up hardens Random and ByteOrdered). The only
    * value not covered after normalization is a partition whose token is exactly
    * the minimum, which is astronomically unlikely (probability 2^-64) and is the
    * same edge every token-range scanner accepts.
    */
  def normalizeUpperBound(end: Token): Token = end match {
    case murmur3: Murmur3Token if murmur3.getValue == Long.MinValue => Murmur3Max
    case other => other
  }
}
