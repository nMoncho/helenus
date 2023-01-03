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
package internal.codec.collection

import scala.collection.immutable.SortedMap

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.internal.codec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SortedMapCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[SortedMap[String, Int]] {

  override protected val codec: TypeCodec[SortedMap[String, Int]] = Codec[SortedMap[String, Int]]

  "SortedMapCodec" should {
    val value = SortedMap("Foo" -> 1, "Bar" -> 2, "Baz" -> 3)
    "encode" in {
      encode(null) shouldBe None
      encode(SortedMap.empty[String, Int]) shouldBe Some("0x00000000")
      encode(value) shouldBe Some(
        "0x000000030000000342617200000004000000020000000342617a000000040000000300000003466f6f0000000400000001"
      )
    }

    "decode" in {
      decode(null) shouldBe Some(Map.empty[String, Int])
      decode("0x00000000") shouldBe Some(Map.empty[String, Int])
      decode(
        "0x0000000300000003466f6f00000004000000010000000342617200000004000000020000000342617a0000000400000003"
      ) shouldBe Some(
        value
      )
    }

    "format" in {
      format(SortedMap.empty[String, Int]) shouldBe "{}"
      format(value) shouldBe "{'Bar':2,'Baz':3,'Foo':1}"
    }

    "parse" in {
      parse("") shouldBe null
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase) shouldBe null
      parse("{}") shouldBe Map.empty[String, Int]
      parse("{'Foo':1,'Bar':2,'Baz':3}") shouldBe value
      parse(" { 'Foo' : 1 , 'Bar' : 2 , 'Baz' : 3 } ") shouldBe value
    }

    "fail to parse invalid input" in {
      val invalid = Seq(
        "Foo:1,Bar:2,Baz:3}",
        "{Foo:1,Bar:2,Baz:3",
        "{{Foo:1,Bar:2,Baz:3}",
        "{Foo,Bar:2,Baz:3}",
        "{Foo:1 Bar:2,Baz:3}"
      )

      invalid.foreach { input =>
        intercept[IllegalArgumentException] {
          parse(input)
        }
      }
    }

    "accept generic type" in {
      val anotherCodec    = Codec[SortedMap[String, Int]]
      val mapCodec        = Codec[Map[String, Int]]
      val regularMapCodec = Codec[SortedMap[String, String]]
      val seqCodec        = Codec[Seq[(String, Int)]]

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherCodec.getJavaType) shouldBe true
      codec.accepts(mapCodec.getJavaType) shouldBe false
      codec.accepts(regularMapCodec.getJavaType) shouldBe false
      codec.accepts(seqCodec.getJavaType) shouldBe false
    }

    "accept objects" in {
      codec.accepts(value) shouldBe true
      codec.accepts(SortedMap(1 -> "Foo")) shouldBe false
    }
  }
}
