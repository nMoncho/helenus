/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object ByteCodec extends FixedSizePrimitiveCodec[Byte](DataTypes.TINYINT, 1, "byte") {

  protected def defaultValue: Byte = 0

  protected def readValue(bytes: ByteBuffer): Byte = bytes.get(bytes.position)

  protected def parseValue(value: String): Byte = value.toByte

  def encode(value: Byte, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).put(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Byte]

}
