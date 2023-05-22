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

import scala.collection.compat._

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.internal.codec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class OnParJavaCodecSpec[
    ScalaColl[_] <: scala.collection.Iterable[_],
    JavaColl[_] <: java.util.Collection[_]
](name: String)(
    implicit factory: Factory[String, ScalaColl[String]]
) extends AnyWordSpec
    with Matchers
    with CodecSpecBase[ScalaColl[String]]
    with OnParCodecSpec[ScalaColl[String], JavaColl[String]] {

  override protected val codec: TypeCodec[ScalaColl[String]]

  override val javaCodec: TypeCodec[JavaColl[String]]

  override def toJava(t: ScalaColl[String]): JavaColl[String]

  private val emptyColl = factory.newBuilder.result()
  private val fooBarColl = {
    val builder = factory.newBuilder
    builder ++= Seq("foo", "bar")
    builder.result()
  }

  name should {
    "on par with Java Codec (encode-decode)" in testEncodeDecode(
      null.asInstanceOf[ScalaColl[String]],
      emptyColl,
      fooBarColl
    )

    "on par with Java Codec (parse-format)" in testParseFormat(
      null.asInstanceOf[ScalaColl[String]],
      emptyColl,
      fooBarColl
    )
  }
}
