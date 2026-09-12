/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.DriverTimeoutException
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Pure unit tests for the retry classification and the range-read failure, so the
  * timeout-splitting policy is covered without needing a cluster.
  */
class RetryPolicySpec extends AnyWordSpec with Matchers {

  "RetryPolicy" should {
    "expose the Default and NoRetry split counts" in {
      RetryPolicy.Default.maxSplits shouldBe 4
      RetryPolicy.NoRetry.maxSplits shouldBe 0
    }

    "treat a DriverTimeoutException as retryable" in {
      RetryPolicy.isRetryable(new DriverTimeoutException("slow")) shouldBe true
    }

    "treat an unrelated exception as not retryable" in {
      RetryPolicy.isRetryable(new RuntimeException("boom")) shouldBe false
    }

    "follow the cause chain to find a retryable timeout" in {
      val wrapped = new RuntimeException("wrapper", new DriverTimeoutException("slow"))
      RetryPolicy.isRetryable(wrapped) shouldBe true
    }

    "stop at a missing cause" in {
      RetryPolicy.isRetryable(new RuntimeException("no cause")) shouldBe false
    }
  }

  "TokenRangeReadException" should {
    "name the range it failed to read and keep the cause" in {
      val range = new Murmur3TokenRange(new Murmur3Token(0L), new Murmur3Token(100L))
      val cause = new DriverTimeoutException("slow")
      val ex    = new TokenRangeReadException(range, cause)

      ex.range shouldBe range
      ex.getMessage should include("Failed to read token range")
      ex.getCause shouldBe cause
    }
  }
}
