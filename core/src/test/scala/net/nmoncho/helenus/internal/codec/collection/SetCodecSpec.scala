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

class SetCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Set[Int]] {

  override protected val codec: TypeCodec[Set[Int]] = Codec[Set[Int]]

  "SetCodec" should {
    "encode" in {
      encode(null) shouldBe None
      encode(Set.empty[Int]) shouldBe Some("0x00000000")
      encode(Set(1, 2, 3)) shouldBe Some(
        "0x00000003000000040000000100000004000000020000000400000003"
      )
    }

    "decode" in {
      decode(null) shouldBe Some(Set.empty[Int])
      decode("0x00000000") shouldBe Some(Set.empty[Int])
      decode("0x00000003000000040000000100000004000000020000000400000003") shouldBe Some(
        Set(1, 2, 3)
      )
    }

    "fail to encode" in {
      val codec = Codec[Set[String]]
      intercept[IllegalArgumentException](
        codec.encode(Set("1", null, "3"), ProtocolVersion.DEFAULT)
      )
    }

    "format" in {
      format(Set()) shouldBe "{}"
      format(Set(1, 2, 3)) shouldBe "{1,2,3}"
    }

    "parse" in {
      parse("") shouldBe null
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase) shouldBe null
      parse("{}") shouldBe Set.empty[Int]
      parse("{1,2,3}") shouldBe Set(1, 2, 3)
      parse(" { 1 , 2 , 3 } ") shouldBe Set(1, 2, 3)
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
      val anotherCodec   = Codec[Set[Int]]
      val stringSetCodec = Codec[Set[String]]
      val intVectorCodec = Codec[Vector[Int]]

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherCodec.getJavaType) shouldBe true
      codec.accepts(stringSetCodec.getJavaType) shouldBe false
      codec.accepts(intVectorCodec.getJavaType) shouldBe false
    }

    "accept objects" in {
      codec.accepts(Set(1, 2, 3)) shouldBe true
      codec.accepts(Set("foo", "bar")) shouldBe false
    }
  }
}

class OnParSetCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[Set[String]]
    with OnParCodecSpec[Set[String], java.util.Set[String]] {

  "SetCodec" should {
    "on par with Java Codec (encode-decode)" in testEncodeDecode(
      null,
      Set(),
      Set("foo", "bar")
    )

    "on par with Java Codec (parse-format)" in testParseFormat(
      null,
      Set(),
      Set("foo", "bar")
    )
  }

  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[Set[String]] = Codec[Set[String]]

  override def javaCodec: TypeCodec[util.Set[String]] = TypeCodecs.setOf(TypeCodecs.TEXT)

  override def toJava(t: Set[String]): util.Set[String] = if (t == null) null else t.asJava
}
