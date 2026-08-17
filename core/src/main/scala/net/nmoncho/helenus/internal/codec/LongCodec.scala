/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object LongCodec extends FixedSizePrimitiveCodec[Long](DataTypes.BIGINT, 8, "64-bits integer") {

  protected def defaultValue: Long = 0

  protected def readValue(bytes: ByteBuffer): Long = bytes.getLong(bytes.position)

  protected def parseValue(value: String): Long = value.toLong

  def encode(value: Long, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).putLong(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Long]

}
