/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec

import java.math.BigInteger

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BigIntCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[BigInt]
    with OnParCodecSpec[BigInt, java.math.BigInteger] {

  override protected val codec: TypeCodec[BigInt] = Codec[BigInt]

  "BigIntCodec" should {
    "encode" in {
      encode(BigInt(1)) shouldBe Some("0x01")
      encode(BigInt(128)) shouldBe Some("0x0080")
      encode(null) shouldBe None
    }

    "decode" in {
      decode("0x01") shouldBe Some(BigInt(1))
      decode("0x0080") shouldBe Some(BigInt(128))
      decode("0x") shouldBe None
      decode(null) shouldBe None
    }

    "format" in {
      format(BigInt(1)) shouldBe "1"
      format(null) shouldBe NULL
    }

    "parse" in {
      parse("1") shouldBe BigInt(1)
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase()) shouldBe null
      parse("") shouldBe null
      parse(null) shouldBe null
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a big int")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[BigInt])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[BigInt]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(BigInt(123)) shouldBe true
      codec.accepts(Int.MaxValue) shouldBe false
    }

    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      null,
      BigInt(0),
      BigInt(1),
      BigInt(123)
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      null,
      BigInt(0),
      BigInt(1),
      BigInt(123)
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[BigInteger] = TypeCodecs.VARINT

  override def toJava(t: BigInt): BigInteger = if (t == null) null else t.bigInteger
}
