/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.math.BigInteger

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenFactory
import com.datastax.oss.driver.internal.core.metadata.token.RandomToken
import com.datastax.oss.driver.internal.core.metadata.token.RandomTokenFactory
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TokenCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Token] {

  override protected val codec: TypeCodec[Token] = TypeCodecs.tokenCodec

  "TokenCodec" should {
    "encode" in {
      encode(new Murmur3Token(0L)) shouldBe Some("0x0000000000000000")
      encode(Murmur3TokenFactory.MAX_TOKEN) shouldBe Some("0x7fffffffffffffff")
      encode(Murmur3TokenFactory.MIN_TOKEN) shouldBe Some("0x8000000000000000")

      encode(new RandomToken(new BigInteger("0", 10))) shouldBe Some("0x00")
      encode(RandomTokenFactory.MAX_TOKEN) shouldBe Some("0x0080000000000000000000000000000000")
      encode(RandomTokenFactory.MIN_TOKEN) shouldBe Some("0xff")

      encode(null) shouldBe None
    }

    "decode" in {
      decode("0x0000000000000000") shouldBe Some(new Murmur3Token(0L))
      decode("0x7fffffffffffffff") shouldBe Some(Murmur3TokenFactory.MAX_TOKEN)
      decode("0x8000000000000000") shouldBe Some(Murmur3TokenFactory.MIN_TOKEN)

      decode(null) shouldBe None
    }

    "format" in {
      format(new Murmur3Token(0L)) shouldBe "0"
      format(Murmur3TokenFactory.MAX_TOKEN) shouldBe "9223372036854775807"
      format(Murmur3TokenFactory.MIN_TOKEN) shouldBe "-9223372036854775808"

      format(new RandomToken(new BigInteger("0", 10))) shouldBe "0"
      format(RandomTokenFactory.MAX_TOKEN) shouldBe "170141183460469231731687303715884105728"
      format(RandomTokenFactory.MIN_TOKEN) shouldBe "-1"

      format(null) shouldBe NULL
    }

    "parse" in {
      parse("0") shouldBe new Murmur3Token(0L)
      parse("9223372036854775807") shouldBe Murmur3TokenFactory.MAX_TOKEN
      parse("-9223372036854775808") shouldBe Murmur3TokenFactory.MIN_TOKEN
      parse(NULL) shouldBe null
    }
  }
}
