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

class MappedCodecSpec extends AnyWordSpec with CodecSpecBase[Int] with Matchers {

  override protected val codec: TypeCodec[Int] =
    Codec.mappingCodec[String, Int](_.toInt, _.toString)

  "MappingCodec" should {
    "encode" in {
      encode(1) shouldBe Some("0x31")
    }

    "decode" in {
      decode("0x31") shouldBe Some(1)
    }

    "format" in {
      format(1) shouldBe "'1'"
    }

    "parse" in {
      parse("'1'") shouldBe 1
    }
  }
}
