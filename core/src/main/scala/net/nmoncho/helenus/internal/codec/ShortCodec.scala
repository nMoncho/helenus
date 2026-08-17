/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object ShortCodec extends FixedSizePrimitiveCodec[Short](DataTypes.SMALLINT, 2, "16-bits integer") {

  protected def defaultValue: Short = 0

  protected def readValue(bytes: ByteBuffer): Short = bytes.getShort(bytes.position)

  protected def parseValue(value: String): Short = value.toShort

  def encode(value: Short, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).putShort(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Short]

}
