/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec.collection

import scala.collection.immutable.SortedSet

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.internal.codec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SortedSetCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[SortedSet[Int]] {

  override protected val codec: TypeCodec[SortedSet[Int]] = Codec[SortedSet[Int]]

  "SortedSetCodec" should {
    "encode" in {
      encode(null) shouldBe None
      encode(SortedSet.empty[Int]) shouldBe Some("0x00000000")
      encode(SortedSet(1, 2, 3)) shouldBe Some(
        "0x00000003000000040000000100000004000000020000000400000003"
      )
    }

    "decode" in {
      decode(null) shouldBe Some(Set.empty[Int])
      decode("0x00000000") shouldBe Some(SortedSet.empty[Int])
      decode("0x00000003000000040000000100000004000000020000000400000003") shouldBe Some(
        Set(1, 2, 3)
      )
    }

    "format" in {
      format(SortedSet()) shouldBe "{}"
      format(SortedSet(1, 2, 3)) shouldBe "{1,2,3}"
    }

    "parse" in {
      parse("") shouldBe null
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase) shouldBe null
      parse("{}") shouldBe SortedSet.empty[Int]
      parse("{1,2,3}") shouldBe SortedSet(1, 2, 3)
      parse(" { 1 , 2 , 3 } ") shouldBe SortedSet(1, 2, 3)
    }

    "fail to parse invalid input" in {
      val invalid = Seq(
        "1,2,3}",
        "{1,2,3",
        "{1 2,3}",
        "{{1,2,3}"
      )

      invalid.foreach { input =>
        intercept[IllegalArgumentException] {
          parse(input)
        }
      }
    }

    "accept generic type" in {
      val anotherCodec = Codec[SortedSet[Int]]
      val setCodec     = Codec[Set[Int]]
      val seqCodec     = Codec[Seq[Int]]

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherCodec.getJavaType) shouldBe true
      codec.accepts(setCodec.getJavaType) shouldBe false
      codec.accepts(seqCodec.getJavaType) shouldBe false
    }

    "accept objects" in {
      codec.accepts(SortedSet(1, 2, 3)) shouldBe true
      codec.accepts(SortedSet("foo", "bar")) shouldBe false
    }
  }
}
