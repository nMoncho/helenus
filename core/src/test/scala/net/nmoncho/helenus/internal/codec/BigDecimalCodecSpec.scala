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

class BigDecimalCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[BigDecimal]
    with OnParCodecSpec[BigDecimal, java.math.BigDecimal] {

  override protected val codec: TypeCodec[BigDecimal] = Codec[BigDecimal]

  "BigDecimalCodec" should {
    "encode" in {
      encode(BigDecimal(1)) shouldBe Some(
        "0x"
          + "00000000" // scale
          + "01" // unscaled value
      )
      encode(BigDecimal(128, 4)) shouldBe Some(
        "0x"
          + "00000004" // scale
          + "0080" // unscaled value
      )
    }

    "decode" in {
      decode("0x0000000001") shouldBe Some(BigDecimal(1))
      decode("0x000000040080") shouldBe Some(BigDecimal(128, 4))
      // decode("0x") shouldBe None FIXME!
    }

    "fail to decode if not enough bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x0000")
      }
    }

    "format" in {
      format(BigDecimal(1)) shouldBe "1"
      format(BigDecimal(128, 4)) shouldBe "0.0128"
      format(null) shouldBe "NULL"
    }

    "parse" in {
      parse("1") shouldBe BigDecimal(1)
      parse("0.0128") shouldBe BigDecimal(128, 4)
      parse("null") shouldBe null
      parse("") shouldBe null
      parse(null) shouldBe null
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a decimal")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[BigDecimal])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[BigDecimal]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(BigDecimal(128, 4)) shouldBe true
      codec.accepts(Double.MaxValue) shouldBe false
    }

    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      null,
      BigDecimal(0),
      BigDecimal(1),
      BigDecimal(128, 4)
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      null,
      BigDecimal(0),
      BigDecimal(1),
      BigDecimal(128, 4)
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[java.math.BigDecimal] = TypeCodecs.DECIMAL

  override def toJava(t: BigDecimal): java.math.BigDecimal = if (t == null) null else t.bigDecimal
}
