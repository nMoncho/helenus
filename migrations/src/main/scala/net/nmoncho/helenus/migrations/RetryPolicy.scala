/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.DriverTimeoutException
import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import com.datastax.oss.driver.api.core.servererrors.ReadTimeoutException

/** Controls how a token range that times out on read is retried by splitting it
  * into halves. A narrower range reads fewer rows, so the retry is more likely to
  * complete within the driver timeout.
  *
  * @param maxSplits how many times a single range may be halved before the read is
  *                  failed (each split roughly halves the rows scanned)
  */
final case class RetryPolicy(maxSplits: Int)

object RetryPolicy {

  /** Halve a timed-out range up to four times (down to about 1/16 of the original). */
  val Default: RetryPolicy = RetryPolicy(maxSplits = 4)

  /** Never retry: the first timeout fails the read. */
  val NoRetry: RetryPolicy = RetryPolicy(maxSplits = 0)

  /** True for the driver timeouts that splitting a range can plausibly recover from. */
  def isRetryable(t: Throwable): Boolean =
    t match {
      case _: DriverTimeoutException => true
      case _: ReadTimeoutException => true
      case other => Option(other.getCause).exists(isRetryable)
    }
}

/** Raised when a token range still times out after a [[RetryPolicy]]'s retries are
  * exhausted, naming the range that could not be read so the failure is actionable.
  */
final class TokenRangeReadException(val range: TokenRange, cause: Throwable)
    extends RuntimeException(
      s"Failed to read token range (${range.getStart}, ${range.getEnd}] after exhausting split retries",
      cause
    )
