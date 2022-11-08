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

package net.nmoncho.helenus.api

import net.nmoncho.helenus.api.RowMapperSpec.IceCream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RowMapperSpec extends AnyWordSpec with Matchers {
  import net.nmoncho.helenus._

  "RowMapper" should {
    "semi-auto derive on companion object" in {
      IceCream.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[RowMapper[IceCream]] shouldBe IceCream.rowMapper
      }
    }

    "produce instances for tuples" in {
      RowMapper[(String, Int)] should not be null

      withClue("and should be implicitly available") {
        implicitly[RowMapper[(String, Int)]] should not be null
      }
    }

    "produce instances for simple types" in {
      RowMapper[String] should not be null

      withClue("and should be implicitly available") {
        implicitly[RowMapper[String]] should not be null
      }
    }
  }

}

object RowMapperSpec {

  import net.nmoncho.helenus._
  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  object IceCream {
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
  }
}
