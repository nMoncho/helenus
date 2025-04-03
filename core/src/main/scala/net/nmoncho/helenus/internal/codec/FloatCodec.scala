/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

object FloatCodec extends TypeCodec[Float] {

  def encode(value: Float, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(4).putFloat(0, value)

  def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Float =
    if (bytes == null || bytes.remaining == 0) 0
    else if (bytes.remaining != 4)
      throw new IllegalArgumentException(
        s"Invalid 32-bits float value, expecting 4 bytes but got [${bytes.remaining}]"
      )
    else bytes.getFloat(bytes.position)

  val getCqlType: DataType = DataTypes.FLOAT

  val getJavaType: GenericType[Float] = GenericType.of(classOf[Float])

  def format(value: Float): String =
    value.toString

  def parse(value: String): Float =
    try {
      if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) 0
      else value.toFloat
    } catch {
      case e: NumberFormatException =>
        throw new IllegalArgumentException(s"Cannot parse 32-bits float value from [$value]", e)
    }

  override def accepts(javaClass: Class[_]): Boolean = javaClass == classOf[Float]

  override def accepts(javaType: GenericType[_]): Boolean = javaType == getJavaType

  override def accepts(value: Any): Boolean = value.isInstanceOf[Float]

}
