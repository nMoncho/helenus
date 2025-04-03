/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.compat

import java.util.concurrent.CompletionStage

import scala.concurrent.Future

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FutureConvertersSpec extends AnyWordSpec with Matchers {
  import FutureConverters._

  "FutureConverters" should {
    "convert scala.concurrent.Future <-> CompletionStage (extension methods)" in {
      val future = Future.successful("foo")

      future.asJava shouldBe a[CompletionStage[String]]

      future.asJava.asScala shouldBe a[Future[String]]
    }

    "convert scala.concurrent.Future <-> CompletionStage" in {
      val future = Future.successful("foo")

      FutureConverters.asJava(future) shouldBe a[CompletionStage[String]]

      FutureConverters.asScala(FutureConverters.asJava(future)) shouldBe a[Future[String]]
    }
  }

}
