/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.data.TupleValue
import com.datastax.oss.driver.api.core.detach.AttachmentPoint
import com.datastax.oss.driver.internal.core.`type`.DefaultTupleType
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TupleCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[(String, String)] {

  override protected val codec: TypeCodec[(String, String)] = Codec.tupleOf[(String, String)]

  "Tuple2Codec" should {
    "encode" in {
      encode(null) shouldBe None
      encode("foo" -> "bar") shouldBe Some("0x00000003666f6f00000003626172")
      encode("bar" -> "foo") shouldBe Some("0x0000000362617200000003666f6f")
      encode("" -> "foo") shouldBe Some("0x0000000000000003666f6f")
      encode("bar" -> "") shouldBe Some("0x0000000362617200000000")
      encode(null.asInstanceOf[String] -> "foo") shouldBe Some("0xffffffff00000003666f6f")
      encode("bar" -> null.asInstanceOf[String]) shouldBe Some("0x00000003626172ffffffff")
    }

    "decode" in {
      decode(null) shouldBe None
      decode("0x00000003666f6f00000003626172") shouldBe Some("foo" -> "bar")
      decode("0x0000000362617200000003666f6f") shouldBe Some("bar" -> "foo")
      decode("0x0000000000000003666f6f") shouldBe Some("" -> "foo")
      decode("0x0000000362617200000000") shouldBe Some("bar" -> "")
      decode("0xffffffff00000003666f6f") shouldBe Some(null.asInstanceOf[String] -> "foo")
      decode("0x00000003626172ffffffff") shouldBe Some("bar" -> null.asInstanceOf[String])
    }

    "format" in {
      format("foo" -> "bar") shouldBe "('foo','bar')"
      format("foo" -> null.asInstanceOf[String]) shouldBe "('foo',NULL)"
      format(null.asInstanceOf[String] -> "bar") shouldBe "(NULL,'bar')"
    }

    "parse" in {
      parse("") shouldBe null
      parse(NULL) shouldBe null
      parse(NULL.toLowerCase()) shouldBe null
      parse(null).asInstanceOf[AnyRef] shouldBe null

      parse("('foo','bar')") shouldBe "foo" -> "bar"
      parse("(NULL,'bar')") shouldBe null.asInstanceOf[String] -> "bar"
      parse(" ( 'foo' , 'bar' ) ") shouldBe "foo" -> "bar"
    }

    "fail to parse invalid input" in {
      val invalid = Seq(
        "('foo')",
        "('foo','bar','baz')",
        "(('foo','bar')",
        "('foo','bar'",
        "('foo' 'bar')",
        "('foo', 123)",
        "'foo', 123)",
        "('foo',"
      )

      invalid.foreach { input =>
        intercept[IllegalArgumentException] {
          parse(input)
        }
      }
    }

    "accept generic type" in {
      val anotherTupleCodec = Codec.tupleOf[(String, String)]
      val intTupleCodec     = Codec.tupleOf[(Int, String)]

      codec.accepts(codec.getJavaType) shouldBe true
      codec.accepts(anotherTupleCodec.getJavaType) shouldBe true
      codec.accepts(intTupleCodec.getJavaType) shouldBe false
    }

    "accept objects" in {
      codec.accepts("foo" -> "bar") shouldBe true
      codec.accepts("foo" -> 1) shouldBe false
      codec.accepts(("foo", "bar", "bar")) shouldBe false
      codec.accepts(List("foo", "bar")) shouldBe false
      codec.accepts("foobar") shouldBe false
    }

  }
}

class OnParTupleCodecSpec
    extends AnyWordSpec
    with Matchers
    with CodecSpecBase[(String, Int)]
    with OnParCodecSpec[(String, Int), TupleValue]
    with BeforeAndAfterAll {

  import org.mockito.Mockito._

  import scala.jdk.CollectionConverters._

  private val attachmentPoint = mock(classOf[AttachmentPoint])
  private val codecRegistry   = mock(classOf[CodecRegistry])
  private val tupleType =
    new DefaultTupleType(List(DataTypes.TEXT, DataTypes.INT).asJava, attachmentPoint)

  "TupleCodec" should {
    "be on par with Java Codec (encode-decode)" in testEncodeDecode(
      null,
      "Foo" -> 1,
      "Bar" -> 42,
      "A Long string" -> 123
    )

    "be on par with Java Codec (parse-format)" in testParseFormat(
      null,
      "Foo" -> 1,
      "Bar" -> 42,
      "A Long string" -> 123
    )

    "be work with the same CQL Type" in testDataTypes()
  }

  override protected val codec: TypeCodec[(String, Int)] = Codec.tupleOf[(String, Int)]

  override def javaCodec: TypeCodec[TupleValue] = TypeCodecs.tupleOf(tupleType)

  override def toJava(t: (String, Int)): TupleValue = if (t == null) null
  else {
    tupleType
      .newValue()
      .setString(0, t._1)
      .setInt(1, t._2)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    when(attachmentPoint.getCodecRegistry).thenReturn(codecRegistry)
    when(attachmentPoint.getProtocolVersion).thenReturn(ProtocolVersion.DEFAULT)

    // Called by the getters/setters
    when(codecRegistry.codecFor(DataTypes.INT, classOf[java.lang.Integer]))
      .thenReturn(TypeCodecs.INT)
    when(codecRegistry.codecFor(DataTypes.TEXT, classOf[String])).thenReturn(TypeCodecs.TEXT)

    // Called by format/parse
    when(codecRegistry.codecFor[java.lang.Integer](DataTypes.INT)).thenReturn(TypeCodecs.INT)
    when(codecRegistry.codecFor[String](DataTypes.TEXT)).thenReturn(TypeCodecs.TEXT)
  }
}
