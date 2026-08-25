/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.sink

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit coverage for the CQL-first sink's [[CassandraSink.Config]]. */
final class CassandraSinkConfigSpec extends AnyWordSpec with Matchers {

  private def roundTrip[A](value: A): A = {
    val bytes = new ByteArrayOutputStream()
    val out   = new ObjectOutputStream(bytes)
    out.writeObject(value)
    out.close()

    val in       = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
    val restored = in.readObject().asInstanceOf[A]
    in.close()
    restored
  }

  "CassandraSink.Config" should {
    "default to per-record, idempotent execution and a no-op failure handler" in {
      val config = CassandraSink.Config()

      config.batchSize shouldBe 1
      config.idempotent shouldBe true
      noException should be thrownBy config.failureHandler(new RuntimeException("ignored"))
    }

    "reject a non-positive batch size" in {
      an[IllegalArgumentException] should be thrownBy CassandraSink.Config(batchSize = 0)
    }

    "carry a non-idempotent setting (for counters and non-idempotent conditionals)" in {
      CassandraSink.Config(idempotent = false).idempotent shouldBe false
    }

    "carry a custom batch size and failure handler" in {
      val seen   = new AtomicInteger(0)
      val config =
        CassandraSink.Config(batchSize = 3, failureHandler = _ => { seen.incrementAndGet(); () })

      config.batchSize shouldBe 3
      config.failureHandler(new RuntimeException("boom"))
      seen.get() shouldBe 1
    }

    "serialize, since it crosses to executors" in {
      val restored = roundTrip(CassandraSink.Config(batchSize = 5))

      restored.batchSize shouldBe 5
      noException should be thrownBy restored.failureHandler(new RuntimeException("ignored"))
    }
  }
}
