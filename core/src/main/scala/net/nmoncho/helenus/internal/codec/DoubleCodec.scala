/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes

object DoubleCodec extends FixedSizePrimitiveCodec[Double](DataTypes.DOUBLE, 8, "64-bits double") {

  protected def defaultValue: Double = 0

  protected def readValue(bytes: ByteBuffer): Double = bytes.getDouble(bytes.position)

  protected def parseValue(value: String): Double = value.toDouble

  def encode(value: Double, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(byteSize).putDouble(0, value)

  override def accepts(value: Any): Boolean = value.isInstanceOf[Double]

}
