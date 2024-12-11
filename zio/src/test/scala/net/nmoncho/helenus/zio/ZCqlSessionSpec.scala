/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
