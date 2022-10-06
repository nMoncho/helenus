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

class LongCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Long]
    with OnParCodecSpec[Long, java.lang.Long] {

  override protected val codec: TypeCodec[Long] = Codec[Long]

  "LongCodec" should {
    "encode" in {
      encode(0L) shouldBe Some("0x0000000000000000")
      encode(1L) shouldBe Some("0x0000000000000001")
    }

    "decode" in {
      decode("0x0000000000000000") shouldBe Some(0L)
      decode("0x0000000000000001") shouldBe Some(1L)
      decode("0x") shouldBe Some(0)
      decode(null) shouldBe Some(0)
    }

    "fail to decode if too many bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x0000")
      }
    }

    "format" in {
      format(0L) shouldBe "0"
      format(1L) shouldBe "1"
    }

    "parse" in {
      parse("0") shouldBe 0L
      parse("1") shouldBe 1L
      parse(NULL) shouldBe 0L
      parse(NULL.toLowerCase()) shouldBe 0L
      parse("") shouldBe 0L
      parse(null) shouldBe 0L
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a long")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Long])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Long]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(123L) shouldBe true
      codec.accepts(Long.MinValue) shouldBe true
      codec.accepts(Int.MinValue) shouldBe false
    }

    // Can't test 'null' since 'Long' extends 'AnyVal'
    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      0L,
      1L,
      123L
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      0L,
      1L,
      123L
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[lang.Long] = TypeCodecs.BIGINT

  override def toJava(t: Long): lang.Long = t
}
