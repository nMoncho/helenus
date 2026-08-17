/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object BooleanCodec extends FixedSizePrimitiveCodec[Boolean](DataTypes.BOOLEAN, 1, "boolean") {

  private val TRUE  = ByteBuffer.wrap(Array[Byte](1))
  private val FALSE = ByteBuffer.wrap(Array[Byte](0))

  protected def defaultValue: Boolean = false

  protected def readValue(bytes: ByteBuffer): Boolean = bytes.get(bytes.position()) != 0

  protected def parseValue(value: String): Boolean = value.toBoolean

  def encode(value: Boolean, protocolVersion: ProtocolVersion): ByteBuffer =
    if (value) TRUE.duplicate() else FALSE.duplicate()

  override def accepts(value: Any): Boolean = value.isInstanceOf[Boolean]

}
