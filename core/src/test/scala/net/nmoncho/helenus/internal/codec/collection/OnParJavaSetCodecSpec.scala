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
