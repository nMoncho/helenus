/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

import zio.Scope
import zio.test.Spec
import zio.test.TestAspect
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object ZCqlSessionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Throwable] = suite("ZCqlSession")(
    simpleOpenTest,
    openFromConfigTest
  ) @@ TestAspect.sequential

  private val simpleOpenTest = test("should be open with simple values") {
    for {
      session <- ZCqlSession.open(ZDefaultCqlSession(host = "localhost", port = 9142))
      _ <- session.close()
    } yield assertTrue(true)
  }

  private val openFromConfigTest = test("should be open with simple values") {
    for {
      session <- ZCqlSession.open("zio-open-from-config")
      _ <- session.close()
    } yield assertTrue(true)
  }
}
