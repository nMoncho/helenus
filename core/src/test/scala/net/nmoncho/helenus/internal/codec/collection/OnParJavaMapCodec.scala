/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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
