/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object IntCodec extends FixedSizePrimitiveCodec[Int](DataTypes.INT, 4, "32-bits integer") {

  protected def defaultValue: Int = 0

  protected def readValue(bytes: ByteBuffer): Int = bytes.getInt(bytes.position)

  protected def parseValue(value: String): Int = value.toInt

  def encode(value: Int, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).putInt(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Int]

}
