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

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{ DataType, DataTypes }

object ShortCodec extends TypeCodec[Short] {

  def encode(value: Short, protocolVersion: ProtocolVersion): ByteBuffer =
    ByteBuffer.allocate(2).putShort(0, value)

  def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Short =
    if (bytes == null || bytes.remaining == 0) 0
    else if (bytes.remaining != 2)
      throw new IllegalArgumentException(
        s"Invalid 16-bits integer value, expecting 2 bytes but got [${bytes.remaining}]"
      )
    else bytes.getShort(bytes.position)

  val getCqlType: DataType = DataTypes.SMALLINT

  val getJavaType: GenericType[Short] = GenericType.of(classOf[Short])

  def format(value: Short): String =
    value.toString

  def parse(value: String): Short =
    try {
      if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL")) 0
      else value.toShort
    } catch {
      case e: NumberFormatException =>
        throw new IllegalArgumentException(s"Cannot parse 16-bits int value from [$value]", e)
    }

  override def accepts(javaClass: Class[_]): Boolean = javaClass == classOf[Short]

  override def accepts(javaType: GenericType[_]): Boolean = javaType == getJavaType

  override def accepts(value: Any): Boolean = value.isInstanceOf[Short]

}
