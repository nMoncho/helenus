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

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.DefaultMapType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

import java.nio.ByteBuffer
import scala.collection.compat._

abstract class AbstractMapCodec[K, V, M[K, V] <: Map[K, V]](
    keyInner: TypeCodec[K],
    valueInner: TypeCodec[V],
    frozen: Boolean
)(implicit factory: Factory[(K, V), M[K, V]])
    extends TypeCodec[M[K, V]] {

  override def accepts(value: Any): Boolean = value match {
    case m: Map[_, _] =>
      m.forall { case (key, value) =>
        keyInner.accepts(key) && valueInner.accepts(value)
      }

    case _ => false
  }

  override val getCqlType: DataType =
    new DefaultMapType(keyInner.getCqlType, valueInner.getCqlType, frozen)

  override def encode(value: M[K, V], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      // FIXME this seems pretty costly, we iterate the set several times!
      val buffers = List.newBuilder[ByteBuffer]
      for ((k, v) <- value) {
        if (k == null) {
          throw new IllegalArgumentException("Map keys cannot be null")
        }
        if (v == null) {
          throw new IllegalArgumentException("Map values cannot be null")
        }
        buffers += keyInner.encode(k, protocolVersion)
        buffers += valueInner.encode(v, protocolVersion)
      }

      pack(buffers.result().toArray, value.size, protocolVersion)
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): M[K, V] = {
    val builder = factory.newBuilder

    if (bytes == null || bytes.remaining == 0) builder.result()
    else {
      val input = bytes.duplicate()
      val size  = input.getInt()
      for (_ <- 0 until size) {
        val k = keyInner.decode(readValue(input, protocolVersion), protocolVersion)
        val v = valueInner.decode(readValue(input, protocolVersion), protocolVersion)
        builder += k -> v
      }

      builder.result()
    }
  }

  override def format(value: M[K, V]): String =
    if (value == null) {
      "NULL"
    } else {
      value
        .map { case (key, value) =>
          s"${keyInner.format(key)}:${valueInner.format(value)}" // Using a SB would yield better performance
        }
        .mkString("{", ",", "}")
    }

  override def parse(value: String): M[K, V] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL")) {
      null.asInstanceOf[M[K, V]]
    } else {
      val builder = factory.newBuilder
      var idx     = ParseUtils.skipSpaces(value, 0)
      if (value.charAt(idx) != '{') {
        throw new IllegalArgumentException(
          s"Cannot parse map value from '$value', at character $idx expecting '{' but got '${value.charAt(idx)}''"
        )
      }
      idx = ParseUtils.skipSpaces(value, idx + 1)
      if (value.charAt(idx) == '}') {
        builder.result()
      } else {
        while (idx < value.length) {
          // Parse Key
          val n = ParseUtils.skipCQLValue(value, idx)
          val k = keyInner.parse(value.substring(idx, n))
          idx = ParseUtils.skipSpaces(value, n)
          if (idx >= value.length || value.charAt(idx) != ':') {
            throw new IllegalArgumentException(
              s"Cannot parse map value from '$value', at character $idx expecting ':' but got '${value
                  .charAt(idx)}''"
            )
          }

          // Parse Value
          idx = ParseUtils.skipSpaces(value, idx + 1)
          val nv = ParseUtils.skipCQLValue(value, idx)
          val v  = valueInner.parse(value.substring(idx, nv))
          builder += k -> v

          idx = ParseUtils.skipSpaces(value, nv)
          if (idx >= value.length) {
            throw new IllegalArgumentException(
              s"Malformed map value '$value', missing closing '}'"
            )
          } else if (value.charAt(idx) == '}') {
            return builder.result()
          } else if (value.charAt(idx) != ',') {
            throw new IllegalArgumentException(
              s"Cannot parse map value from '$value', at character $idx expecting ',' but got '${value
                  .charAt(idx)}''"
            )
          }
          idx = ParseUtils.skipSpaces(value, idx + 1)
        }
        throw new IllegalArgumentException(s"Malformed map value '$value', missing closing '}'")
      }
    }

}
