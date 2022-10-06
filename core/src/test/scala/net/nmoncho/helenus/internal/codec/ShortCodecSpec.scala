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

class ShortCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Short]
    with OnParCodecSpec[Short, java.lang.Short] {

  override protected val codec: TypeCodec[Short] = Codec[Short]

  "ShortCodec" should {
    val zero: Short = 0
    "encode" in {
      encode(zero) shouldBe Some("0x0000")
    }

    "decode" in {
      decode("0x0000") shouldBe Some(zero)
      decode("0x") shouldBe Some(zero)
      decode(null) shouldBe Some(zero)
    }

    "fail to decode if too many bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x00000000")
      }
    }

    "format" in {
      format(zero) shouldBe "0"
    }

    "parse" in {
      parse("0") shouldBe zero
      parse(NULL) shouldBe zero
      parse(NULL.toLowerCase()) shouldBe zero
      parse("") shouldBe zero
      parse(null) shouldBe zero
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not an int")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Short])) shouldBe true
      codec.accepts(GenericType.of(classOf[Int])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Short]) shouldBe true
      codec.accepts(classOf[Int]) shouldBe false
    }

    "accept objects" in {
      val oneTwoThree: Short = 123
      codec.accepts(oneTwoThree) shouldBe true
      codec.accepts(Int.MaxValue) shouldBe false
    }

    // Can't test 'null' since 'Short' extends 'AnyVal'
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

  override def javaCodec: TypeCodec[lang.Short] = TypeCodecs.SMALLINT

  override def toJava(t: Short): lang.Short = t
}
