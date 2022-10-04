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
