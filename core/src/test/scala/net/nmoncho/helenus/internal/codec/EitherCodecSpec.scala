/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EitherCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Either[Int, String]] {

  override protected val codec: TypeCodec[Either[Int, String]] = Codec[Either[Int, String]]

  "EitherCodec" should {
    "encode" in {
      encode(Left(1)) shouldBe Some("0x0000000400000001ffffffff")
      encode(Right("foo")) shouldBe Some("0xffffffff00000003666f6f")
    }

    "decode" in {
      decode(null) shouldBe None
      decode("0x0000000400000001ffffffff") shouldBe Some(Left(1))
      decode("0xffffffff00000003666f6f") shouldBe Some(Right("foo"))
    }

    "format" in {
      format(null) shouldBe "NULL"
      format(Left(1)) shouldBe "(1,NULL)"
      format(Right("foo")) shouldBe "(NULL,'foo')"
    }

    "parse" in {
      parse("NULL") shouldBe null
      parse("(1,NULL)") shouldBe Left(1)
      parse("(NULL,'foo')") shouldBe Right("foo")
    }

    "fail to parse invalid input" in {
      val invalid = Seq(
        "(1)",
        "('foo')",
        "((1,NULL)",
        "(NULL,'bar'",
        "(1 'bar')",
        "1, NULL)",
        "(1,",
        "(1"
      )

      invalid.foreach { input =>
        withClue(s"input [${input}] is invalid") {
          intercept[IllegalArgumentException] {
            parse(input)
          }
        }
      }
    }

    "accept generic type" in {
      val anotherCodec        = Codec[Either[Int, String]]
      val stringIntCodec      = Codec[Either[String, Int]]
      val representationCodec = Codec[(Int, String)] // checking this due to encoding

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherCodec.getJavaType) shouldBe true
      codec.accepts(stringIntCodec.getJavaType) shouldBe false
      codec.accepts(representationCodec.getJavaType) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Either[_, _]]) shouldBe true
    }

    "accept objects" in {
      codec.accepts(Left(1)) shouldBe true
      codec.accepts(Right("foo")) shouldBe true
      codec.accepts(Left("foo")) shouldBe false
      codec.accepts(Right(1)) shouldBe false
      codec.accepts("foo") shouldBe false
      codec.accepts(1) shouldBe false
    }
  }
}
