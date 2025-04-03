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

object DoubleCodec extends TypeCodec[Double] {

  def encode(value: Double, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(8).putDouble(0, value)

  def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Double =
    if (bytes == null || bytes.remaining == 0) 0
    else if (bytes.remaining != 8)
      throw new IllegalArgumentException(
        s"Invalid 64-bits double value, expecting 8 bytes but got [${bytes.remaining}]"
      )
    else bytes.getDouble(bytes.position)

  val getCqlType: DataType = DataTypes.DOUBLE

  val getJavaType: GenericType[Double] = GenericType.of(classOf[Double])

  def format(value: Double): String =
    value.toString

  def parse(value: String): Double =
    try {
      if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) 0
      else value.toDouble
    } catch {
      case e: NumberFormatException =>
        throw new IllegalArgumentException(s"Cannot parse 64-bits double value from [$value]", e)
    }

  override def accepts(javaClass: Class[_]): Boolean = javaClass == classOf[Double]

  override def accepts(javaType: GenericType[_]): Boolean = javaType == getJavaType

  override def accepts(value: Any): Boolean = value.isInstanceOf[Double]

}
