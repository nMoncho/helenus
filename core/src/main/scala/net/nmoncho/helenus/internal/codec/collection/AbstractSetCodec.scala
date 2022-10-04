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

package net.nmoncho.helenus.internal.codec.collection

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.DefaultSetType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

import scala.collection.compat._

abstract class AbstractSetCodec[T, M[T] <: Set[T]](
    inner: TypeCodec[T],
    frozen: Boolean
)(implicit factory: Factory[T, M[T]])
    extends TypeCodec[M[T]] {

  override def accepts(value: Any): Boolean = value match {
    case l: M[_] @unchecked => l.headOption.fold(true)(inner.accepts)
    case _ => false
  }

  override val getCqlType: DataType = new DefaultSetType(inner.getCqlType, frozen)

  override def encode(value: M[T], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      // FIXME this seems pretty costly, we iterate the set several times!
      val buffers = for (item <- value) yield {
        if (item == null) {
          throw new IllegalArgumentException("Set elements cannot be null")
        }
        inner.encode(item, protocolVersion)
      }

      pack(buffers.toArray, value.size, protocolVersion)
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): M[T] = {
    val builder = factory.newBuilder

    if (bytes == null || bytes.remaining == 0) builder.result()
    else {
      val input = bytes.duplicate()
      val size  = input.getInt()
      for (_ <- 0 until size) {
        builder += inner.decode(
          readValue(input, protocolVersion),
          protocolVersion
        )
      }

      builder.result()
    }
  }

  override def format(value: M[T]): String =
    if (value == null) {
      "NULL"
    } else {
      value.map(inner.format).mkString("{", ",", "}")
    }

  override def parse(value: String): M[T] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL")) {
      null.asInstanceOf[M[T]]
    } else {
      val builder = factory.newBuilder
      var idx     = ParseUtils.skipSpaces(value, 0)
      if (value.charAt(idx) != '{') {
        throw new IllegalArgumentException(
          s"Cannot parse set value from '$value', at character $idx expecting '{' but got '${value.charAt(idx)}''"
        )
      }
      idx = ParseUtils.skipSpaces(value, idx + 1)
      if (value.charAt(idx) == '}') {
        builder.result()
      } else {
        while (idx < value.length) {
          val n = ParseUtils.skipCQLValue(value, idx)
          builder += inner.parse(value.substring(idx, n))

          idx = ParseUtils.skipSpaces(value, n)
          if (idx >= value.length) {
            throw new IllegalArgumentException(
              s"Malformed set value '$value', missing closing '}'"
            )
          } else if (value.charAt(idx) == '}') {
            return builder.result()
          } else if (value.charAt(idx) != ',') {
            throw new IllegalArgumentException(
              s"Cannot parse set value from '$value', at character $idx expecting ',' but got '${value
                  .charAt(idx)}''"
            )
          }
          idx = ParseUtils.skipSpaces(value, idx + 1)
        }
        throw new IllegalArgumentException(s"Malformed set value '$value', missing closing '}'")
      }
    }

}
