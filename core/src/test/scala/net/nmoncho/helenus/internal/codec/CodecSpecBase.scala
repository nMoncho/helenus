/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
