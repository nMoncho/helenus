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

package net.nmoncho.helenus
package internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.{ TypeCodec, TypeCodecs }
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IntCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Int]
    with OnParCodecSpec[Int, java.lang.Integer] {

  override protected val codec: TypeCodec[Int] = Codec[Int]

  "IntCodec" should {
    "encode" in {
      encode(0) shouldBe Some("0x00000000")
    }

    "decode" in {
      decode("0x00000000") shouldBe Some(0)
      decode("0x") shouldBe Some(0)
      decode(null) shouldBe Some(0)
    }

    "fail to decode if too many bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x0000")
      }
    }

    "format" in {
      format(0) shouldBe "0"
    }

    "parse" in {
      parse("0") shouldBe 0
      parse(NULL) shouldBe 0
      parse(NULL.toLowerCase()) shouldBe 0
      parse("") shouldBe 0
      parse(null) shouldBe 0
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not an int")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Int])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Int]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(123) shouldBe true
      codec.accepts(Int.MaxValue) shouldBe true
    }

    // Can't test 'null' since 'Int' extends 'AnyVal'
    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      0,
      1,
      123
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      0,
      1,
      123
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[Integer] = TypeCodecs.INT

  override def toJava(t: Int): Integer = t
}
