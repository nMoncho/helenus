/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeParameter
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

class OptionCodec[T](inner: TypeCodec[T]) extends TypeCodec[Option[T]] {

  override val getJavaType: GenericType[Option[T]] = {
    val typeToken = TypeToken.of(inner.getJavaType.getType).wrap().asInstanceOf[TypeToken[T]]
    val token     = new TypeToken[Option[T]]() {}.where(new TypeParameter[T]() {}, typeToken)

    GenericType.of(token.getType).asInstanceOf[GenericType[Option[T]]]
  }

  override val getCqlType: DataType = inner.getCqlType

  override def encode(value: Option[T], protocolVersion: ProtocolVersion): ByteBuffer =
    value match {
      case Some(value) => inner.encode(value, protocolVersion)
      // This will create a tombstone, although this is how `OptionalCodec` does it.
      // A higher level solution is provided with `ScalaPreparedStatement.setIfDefined`
      case None | null => null
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Option[T] =
    if (bytes == null || bytes.remaining == 0) None
    else Option(inner.decode(bytes, protocolVersion))

  override def format(value: Option[T]): String = value match {
    case Some(value) => inner.format(value)
    case None | null => NULL
  }

  override def parse(value: String): Option[T] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) None
    else Option(inner.parse(value))

  override def accepts(value: Any): Boolean = value match {
    case None => true
    case Some(value) => inner.accepts(value)
    case _ => false
  }

  override def accepts(cqlType: DataType): Boolean =
    inner.accepts(cqlType)

  override def toString: String = s"OptionCodec[${inner.getCqlType.toString}]"

}

object OptionCodec {
  def apply[T](inner: TypeCodec[T]): OptionCodec[T] = new OptionCodec(inner)
}
