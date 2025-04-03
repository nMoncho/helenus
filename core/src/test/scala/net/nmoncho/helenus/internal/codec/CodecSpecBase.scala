/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.protocol.internal.util.Bytes

trait CodecSpecBase[T] {

  protected val codec: TypeCodec[T]

  def encode(t: T, protocolVersion: ProtocolVersion): Option[String] =
    Option(codec.encode(t, protocolVersion)).map(Bytes.toHexString)

  def encode(t: T): Option[String] = encode(t, ProtocolVersion.DEFAULT)

  def decode(hexString: String, protocolVersion: ProtocolVersion): Option[T] = Option(
    codec.decode(
      if (hexString == null) null else Bytes.fromHexString(hexString),
      protocolVersion
    )
  )

  def decode(hexString: String): Option[T] = decode(hexString, ProtocolVersion.DEFAULT)

  def format(t: T): String = codec.format(t)

  def parse(s: String): T = codec.parse(s)

  def quote(value: String): String = s"'${value}'"
}
