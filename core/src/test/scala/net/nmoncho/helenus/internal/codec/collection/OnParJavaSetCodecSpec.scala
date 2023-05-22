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

abstract class OnParJavaSetCodecSpec[ScalaColl[_] <: scala.collection.Set[_]](name: String)(
    implicit factory: Factory[String, ScalaColl[String]]
) extends OnParJavaCodecSpec[ScalaColl, java.util.Set](name) {

  override val javaCodec: TypeCodec[java.util.Set[String]] = TypeCodecs.setOf(TypeCodecs.TEXT)
}

class OnParMutableSetCodecSpec extends OnParJavaSetCodecSpec[mutablecoll.Set]("MutableSetCodec") {

  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[mutablecoll.Set[String]] =
    Codec[mutablecoll.Set[String]]

  override def toJava(t: mutablecoll.Set[String]): util.Set[String] =
    if (t == null) null else t.asJava
}

class OnParSetCodecSpec extends OnParJavaSetCodecSpec[Set]("SetCodec") {

  import scala.jdk.CollectionConverters._

  override protected val codec: TypeCodec[Set[String]] =
    Codec[Set[String]]

  override def toJava(t: Set[String]): util.Set[String] =
    if (t == null) null else t.asJava
}
