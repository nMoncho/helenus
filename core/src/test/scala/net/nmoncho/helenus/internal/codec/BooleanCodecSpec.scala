/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec

import java.lang

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BooleanCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Boolean]
    with OnParCodecSpec[Boolean, java.lang.Boolean] {

  override protected val codec: TypeCodec[Boolean] = Codec[Boolean]

  "BooleanCodec" should {
    "encode" in {
      encode(false) shouldBe Some("0x00")
      encode(true) shouldBe Some("0x01")
    }

    "decode" in {
      decode("0x00") shouldBe Some(false)
      decode("0x01") shouldBe Some(true)
      decode("0x") shouldBe Some(false)
      decode(null) shouldBe Some(false)
    }

    "fail to decode if too many bytes" in {
      intercept[IllegalArgumentException] {
        decode("0x0000")
      }
    }

    "format" in {
      format(false) shouldBe "false"
      format(true) shouldBe "true"
    }

    "parse" in {
      parse("false") shouldBe false
      parse("true") shouldBe true
      parse(NULL) shouldBe false
      parse(NULL.toLowerCase()) shouldBe false
      parse("") shouldBe false
      parse(null) shouldBe false
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("maybe")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Boolean])) shouldBe true
      codec.accepts(GenericType.of(classOf[Int])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Boolean]) shouldBe true
      codec.accepts(classOf[Int]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(false) shouldBe true
      codec.accepts(true) shouldBe true
      codec.accepts(Int.MaxValue) shouldBe false
    }

    // Can't test 'null' since 'Boolean' extends 'AnyVal'
    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      true,
      false
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      true,
      false
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override def javaCodec: TypeCodec[lang.Boolean] = TypeCodecs.BOOLEAN

  override def toJava(t: Boolean): lang.Boolean = t
}
