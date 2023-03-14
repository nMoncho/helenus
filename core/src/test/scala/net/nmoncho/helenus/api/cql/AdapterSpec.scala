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

package net.nmoncho.helenus.api.cql

import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AdapterSpec extends AnyWordSpec with Matchers {

  import HotelsTestData.PointOfInterests._
  import HotelsTestData._

  "Adapter" should {
    "adapt a case class to a tuple" in {
      val adapter = Adapter[Hotel]

      adapter(Hotels.h1) should equal(
        "h1",
        "The James Rotterdam",
        "+31 10 710 9000",
        Address(
          "Wijnhaven 107",
          "Rotterdam",
          "South Holland",
          "3011 WN",
          "Netherlands"
        ),
        Set(rotterdamErasmusBridge.name, rotterdamZoo.name)
      )
    }

    "adapt a case class with a computed column" in {
      val adapter = Adapter
        .builder[Hotel]
        .withComputedColumn(_.name.charAt(0))
        .build

      adapter(Hotels.h1) should equal(
        "h1",
        "The James Rotterdam",
        "+31 10 710 9000",
        Address(
          "Wijnhaven 107",
          "Rotterdam",
          "South Holland",
          "3011 WN",
          "Netherlands"
        ),
        Set(rotterdamErasmusBridge.name, rotterdamZoo.name),
        'T' // computed column
      )
    }
  }
}
