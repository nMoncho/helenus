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

class TupleCodecsSpec extends AnyWordSpec with Matchers {

  "Tuple2Codec (String, Int)" should beAValidTupleCodec(Codec.tupleOf[(String, Int)])(
    "foo" -> 1,
    "bar" -> 3,
    "some long text" -> 4
  )

  "Tuple2Codec (String, Option[Int])" should beAValidTupleCodec(
    Codec.tupleOf[(String, Option[Int])]
  )(
    "foo" -> Some(1),
    "bar" -> None,
    "some long text" -> Some(4)
  )

  "Tuple2Codec (Int, Double)" should beAValidTupleCodec(Codec.tupleOf[(Int, Double)])(
    1 -> 2.0,
    3 -> 4.55,
    5 -> 123.456
  )

  "Tuple3Codec (String, Int, String)" should beAValidTupleCodec(
    Codec.tupleOf[(String, Int, String)]
  )(
    ("foo", 1, "bar"),
    ("bar", 3, "foo"),
    ("some long text", 4, "")
  )

  "Tuple4Codec (Int, Double, String, String)" should beAValidTupleCodec(
    Codec.tupleOf[(Int, Double, String, String)]
  )(
    (1, 2.0, "foo", "bar"),
    (3, 4.55, "tar", "baz"),
    (5, 123.456, "some long text", "")
  )

  def beAValidTupleCodec[T <: Product](c: TypeCodec[T])(testData: T*): Unit = {
    val base = new CodecSpecBase[T] {
      override val codec: TypeCodec[T] = c
    }

    "encode and decode" in {
      base.encode(null.asInstanceOf[T]) shouldBe None
      base.decode(null) shouldBe None

      testData.foreach { value =>
        val encoded = base.encode(value)
        encoded shouldBe defined

        base.decode(encoded.get) shouldBe Some(value)
      }
    }

    "format and parse" in {
      base.format(null.asInstanceOf[T]) shouldBe "NULL"

      // 'null' parsing
      base.parse(null).asInstanceOf[AnyRef] shouldBe null
      base.parse("").asInstanceOf[AnyRef] shouldBe null
      base.parse(NULL).asInstanceOf[AnyRef] shouldBe null
      base.parse(NULL.toLowerCase()).asInstanceOf[AnyRef] shouldBe null

      testData.foreach { value =>
        base.parse(base.format(value)) shouldBe value
      }
    }

    "accept objects" in {
      testData.foreach { value =>
        c.accepts(value) shouldBe true
      }
    }
  }
}
