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

import java.util

import scala.collection.compat._
import scala.collection.{ mutable => mutablecoll }

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import net.nmoncho.helenus.internal.codec._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class OnParJavaMapCodec[ScalaColl[_, _] <: scala.collection.Map[_, _]](name: String)(
    implicit factory: Factory[(String, String), ScalaColl[String, String]]
) extends AnyWordSpec
    with Matchers
    with CodecSpecBase[ScalaColl[String, String]]
    with OnParCodecSpec[ScalaColl[String, String], java.util.Map[String, String]] {

  override protected val codec: TypeCodec[ScalaColl[String, String]]

  override def toJava(t: ScalaColl[String, String]): util.Map[String, String]

  override def javaCodec: TypeCodec[util.Map[String, String]] =
    TypeCodecs.mapOf(TypeCodecs.TEXT, TypeCodecs.TEXT)

  private val emptyColl = factory.newBuilder.result()
  private val fooBarColl = {
    val builder = factory.newBuilder
    builder ++= Seq("foo" -> "bar", "bar" -> "baz")
    builder.result()
  }

  name should {
    "on par with Java Codec (encode-decode)" in testEncodeDecode(
      null.asInstanceOf[ScalaColl[String, String]],
      emptyColl,
      fooBarColl
    )

    "on par with Java Codec (parse-format)" in testParseFormat(
      null.asInstanceOf[ScalaColl[String, String]],
      emptyColl,
      fooBarColl
    )
  }

}

class OnParMapCodec extends OnParJavaMapCodec[Map]("MapCodec") {
  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[Map[String, String]] =
    Codec[Map[String, String]]

  override def toJava(t: Map[String, String]): util.Map[String, String] =
    if (t == null) null else t.asJava
}

class OnParMutableMapCodec extends OnParJavaMapCodec[mutablecoll.Map]("MutableMapCodec") {
  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[mutablecoll.Map[String, String]] =
    Codec[mutablecoll.Map[String, String]]

  override def toJava(t: mutablecoll.Map[String, String]): util.Map[String, String] =
    if (t == null) null else t.asJava
}
