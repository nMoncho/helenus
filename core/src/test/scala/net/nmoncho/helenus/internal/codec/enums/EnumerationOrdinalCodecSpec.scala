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
package internal.codec.enums

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import net.nmoncho.helenus.api.OrdinalEncoded
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs
import net.nmoncho.helenus.internal.codec.CodecSpecBase
import net.nmoncho.helenus.internal.codec.enums.EnumerationOrdinalCodecSpec.Fingers
import net.nmoncho.helenus.internal.codec.enums.EnumerationOrdinalCodecSpec.Fingers.Finger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EnumerationOrdinalCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Finger] {

  override protected val codec: TypeCodec[Finger] = Codec[Finger]

  "EnumerationCodecSpec" should {
    "encode" in {
      encode(Fingers.Ring) shouldBe Some("0x00000003")
      encode(Fingers.Index) shouldBe Some("0x00000001")
      encode(Fingers.Little) shouldBe Some("0x00000004")
    }

    "decode" in {
      decode("0x00000003") shouldBe Some(Fingers.Ring)
      decode("0x00000001") shouldBe Some(Fingers.Index)
      decode("0x00000004") shouldBe Some(Fingers.Little)
    }

    "fail to decode wrong value" in {
      intercept[NoSuchElementException] {
        decode("0x52696e6e")
      }
    }

    "format" in {
      format(Fingers.Ring) shouldBe "3"
      format(Fingers.Index) shouldBe "1"
      format(null) shouldBe "NULL"
    }

    "parse" in {
      parse("3") shouldBe Fingers.Ring
      parse("1") shouldBe Fingers.Index
      parse("null") shouldBe null
      parse("") shouldBe null
      parse(null) shouldBe null
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a finger")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Finger])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Finger]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(Fingers.Index) shouldBe true
      codec.accepts(Double.MaxValue) shouldBe false
    }
  }
}

object EnumerationOrdinalCodecSpec {

  @OrdinalEncoded
  object Fingers extends Enumeration {
    type Finger = Value

    val Thumb, Index, Middle, Ring, Little = Value
  }
}
