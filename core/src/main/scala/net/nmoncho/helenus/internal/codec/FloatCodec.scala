/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object FloatCodec extends FixedSizePrimitiveCodec[Float](DataTypes.FLOAT, 4, "32-bits float") {

  protected def defaultValue: Float = 0

  protected def readValue(bytes: ByteBuffer): Float = bytes.getFloat(bytes.position)

  protected def parseValue(value: String): Float = value.toFloat

  def encode(value: Float, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).putFloat(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Float]

}
