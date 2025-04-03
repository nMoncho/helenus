/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers

trait OnParCodecSpec[T, J] { this: Suite with Matchers with CodecSpecBase[T] =>

  def javaCodec: TypeCodec[J]
  def toJava(t: T): J

  private lazy val jCodec = new CodecSpecBase[J] {
    override protected val codec: TypeCodec[J] = javaCodec
  }

  def testEncodeDecode(input: T*): Unit =
    input.foreach { value =>
      val scalaEncode = encode(value)
      val javaEncode  = jCodec.encode(toJava(value))

      scalaEncode shouldBe javaEncode
      (scalaEncode, javaEncode) match {
        case (Some(scala), Some(java)) =>
          decode(scala).map(toJava) shouldBe jCodec.decode(java)

        case _ => // ignore
      }
    }

  def testParseFormat(input: T*): Unit =
    input.foreach { value =>
      format(value) shouldBe jCodec.format(toJava(value))
      toJava(parse(format(value))) shouldBe jCodec.parse(
        jCodec.format(toJava(value))
      ) // This is going to be a problem with collections
    }

  def testDataTypes(): Unit =
    codec.getCqlType shouldBe javaCodec.getCqlType
}
