/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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

  private val emptyColl  = factory.newBuilder.result()
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
