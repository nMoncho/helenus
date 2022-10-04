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

import java.lang

import com.datastax.oss.driver.api.core.`type`.codec.{ TypeCodec, TypeCodecs }
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FloatCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Float]
    with OnParCodecSpec[Float, java.lang.Float] {

  override protected val codec: TypeCodec[Float] = Codec[Float]

  "FloatCodec" should {
    "encode" in {
      encode(0.0f) shouldBe Some("0x00000000")
    }

    "decode" in {
      decode("0x00000000") shouldBe Some(0.0f)
      decode("0x") shouldBe Some(0.0f)
      decode(null) shouldBe Some(0.0f)
    }

    "fail to decode if too many bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x0000")
      }
    }

    "format" in {
      format(0.0f) shouldBe "0.0"
    }

    "parse" in {
      parse("0.0") shouldBe 0.0f
      parse("NULL") shouldBe 0.0f
      parse("null") shouldBe 0.0f
      parse("") shouldBe 0.0f
      parse(null) shouldBe 0.0f
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a double")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Float])) shouldBe true
      codec.accepts(GenericType.of(classOf[Int])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Float]) shouldBe true
      codec.accepts(classOf[Int]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(123.45f) shouldBe true
      codec.accepts(Float.MaxValue) shouldBe true
      codec.accepts(Int.MaxValue) shouldBe false
    }

    // Can't test 'null' since 'Float' extends 'AnyVal'
    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      0.0f,
      123.45f
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      0.0f,
      123.45f
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[lang.Float] = TypeCodecs.FLOAT

  override def toJava(t: Float): lang.Float = t
}
