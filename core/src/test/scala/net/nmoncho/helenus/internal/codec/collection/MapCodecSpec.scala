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

import com.datastax.oss.driver.api.core.ProtocolVersion

import java.util
import com.datastax.oss.driver.api.core.`type`.codec.{ TypeCodec, TypeCodecs }
import net.nmoncho.helenus.internal.codec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MapCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Map[String, Int]] {

  override protected val codec: TypeCodec[Map[String, Int]] = Codec[Map[String, Int]]

  "MapCodec" should {
    val value = Map("Foo" -> 1, "Bar" -> 2, "Baz" -> 3)
    "encode" in {
      encode(null) shouldBe None
      encode(Map.empty[String, Int]) shouldBe Some("0x00000000")
      encode(value) shouldBe Some(
        "0x0000000300000003466f6f00000004000000010000000342617200000004000000020000000342617a0000000400000003"
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
      format(Map.empty[String, Int]) shouldBe "{}"
      format(value) shouldBe "{'Foo':1,'Bar':2,'Baz':3}"
    }

    "parse" in {
      parse("") shouldBe null
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase) shouldBe null
      parse("{}") shouldBe Map.empty[String, Int]
      parse("{'Foo':1,'Bar':2,'Baz':3}") shouldBe value
      parse(" { 'Foo' : 1 , 'Bar' : 2 , 'Baz' : 3 } ") shouldBe value
    }

    "fail to encode" in {
      val codec     = Codec[Map[String, String]]
      val nullKey   = Map(null.asInstanceOf[String] -> "1")
      val nullValue = Map("foo" -> null.asInstanceOf[String])

      intercept[IllegalArgumentException](codec.encode(nullKey, ProtocolVersion.DEFAULT))
      intercept[IllegalArgumentException](codec.encode(nullValue, ProtocolVersion.DEFAULT))
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
      val anotherCodec = Codec[Map[String, Int]]
      val mapCodec     = Codec[Map[String, String]]
      val seqCodec     = Codec[Seq[(String, Int)]]

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherCodec.getJavaType) shouldBe true
      codec.accepts(mapCodec.getJavaType) shouldBe false
      codec.accepts(seqCodec.getJavaType) shouldBe false
    }

    "accept objects" in {
      codec.accepts(value) shouldBe true
      codec.accepts(Map(1 -> "Foo")) shouldBe false
      codec.accepts(Seq(1 -> "Foo")) shouldBe false
    }
  }
}

class OnParMapCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Map[String, String]]
    with OnParCodecSpec[Map[String, String], java.util.Map[String, String]] {

  "MapCodec" should {
    "on par with Java Codec (encode-decode)" in testEncodeDecode(
      null,
      Map(),
      Map("foo" -> "bar", "bar" -> "baz")
    )

    "on par with Java Codec (parse-format)" in testParseFormat(
      null,
      Map(),
      Map("foo" -> "bar", "bar" -> "baz")
    )
  }

  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[Map[String, String]] =
    Codec[Map[String, String]]

  override def javaCodec: TypeCodec[util.Map[String, String]] =
    TypeCodecs.mapOf(TypeCodecs.TEXT, TypeCodecs.TEXT)

  override def toJava(t: Map[String, String]): util.Map[String, String] =
    if (t == null) null else t.asJava
}
