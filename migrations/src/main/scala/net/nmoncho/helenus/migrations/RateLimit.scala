/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import scala.concurrent.duration.FiniteDuration

/** An opt-in rate limit for a token-range scan: at most `elements` rows per `per`.
  *
  * There is no default rate cap. Supply a [[RateLimit]] only to
  * deliberately protect the source or target cluster. Reactive backpressure already
  * keeps the source from outrunning a slow sink, so this is for rate shaping, not
  * correctness.
  */
final case class RateLimit(elements: Int, per: FiniteDuration)
