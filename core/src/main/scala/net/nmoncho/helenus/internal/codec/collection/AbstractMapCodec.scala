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
import scala.collection.mutable

abstract class AbstractMapCodec[K, V, M[K, V] <: Map[K, V]](
    keyInner: TypeCodec[K],
    valueInner: TypeCodec[V],
    frozen: Boolean
)(implicit factory: Factory[(K, V), M[K, V]])
    extends TypeCodec[M[K, V]] {

  private val openingChar: Char       = '{'
  private val closingChar: Char       = '}'
  private val entrySeparator: Char    = ','
  private val keyValueSeparator: Char = ':'

  override val getCqlType: DataType =
    new DefaultMapType(keyInner.getCqlType, valueInner.getCqlType, frozen)

  override def encode(value: M[K, V], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      val (buffers, size) = value.foldLeft((Vector.empty[ByteBuffer], 4)) {
        case ((buffers, totalSize), (key, value)) =>
          if (key == null) {
            throw new IllegalArgumentException("Map keys cannot be null")
          }
          if (value == null) {
            throw new IllegalArgumentException("Map values cannot be null")
          }

          val encodedKey   = keyInner.encode(key, protocolVersion)
          val encodedValue = valueInner.encode(value, protocolVersion)
          val size         = (4 + encodedKey.remaining()) + (4 + encodedValue.remaining())

          (
            buffers :+ encodedKey :+ encodedValue,
            totalSize + size
          )
      }

      val result = ByteBuffer.allocate(size)
      result.putInt(value.size)
      for (element <- buffers) {
        result.putInt(element.remaining())
        result.put(element)
      }
      result.flip()

      result
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): M[K, V] = {
    val builder = factory.newBuilder

    if (bytes == null || bytes.remaining == 0) builder.result()
    else {
      val input = bytes.duplicate()
      val size  = input.getInt()
      for (_ <- 0 until size) {
        // Allow null elements on the decode path, because Cassandra might return such collections
        // for some computed values in the future -- e.g. SELECT ttl(some_collection)

        // Decode Key
        val keySize = input.getInt()
        val key =
          if (keySize < 0) null.asInstanceOf[K]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + keySize)
            input.position(input.position() + keySize)
            keyInner.decode(copy, protocolVersion)
          }

        // Decode Value
        val valueSize = input.getInt()
        val value =
          if (valueSize < 0) null.asInstanceOf[V]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + valueSize)
            input.position(input.position() + valueSize)

            valueInner.decode(copy, protocolVersion)
          }

        builder += key -> value
      }

      builder.result()
    }
  }

  override def format(map: M[K, V]): String =
    if (map == null) {
      "NULL"
    } else {
      val sb   = new mutable.StringBuilder().append(openingChar)
      var tail = false
      for ((key, value) <- map) {
        if (tail) sb.append(entrySeparator)
        else tail = true

        sb.append(keyInner.format(key))
          .append(keyValueSeparator)
          .append(valueInner.format(value))
      }
      sb.append(closingChar).toString()
    }

  override def parse(value: String): M[K, V] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL")) {
      null.asInstanceOf[M[K, V]]
    } else {
      val builder = factory.newBuilder
      var idx     = ParseUtils.skipSpaces(value, 0)

      if (idx >= value.length) {
        throw new IllegalArgumentException(
          s"Malformed map value '$value', missing opening '$openingChar', but got EOF"
        )
      } else if (value.charAt(idx) != openingChar) {
        throw new IllegalArgumentException(
          s"Cannot parse map value from '$value', at character $idx expecting '$openingChar' but got '${value
              .charAt(idx)}''"
        )
      }

      idx = ParseUtils.skipSpaces(value, idx + 1)
      if (value.charAt(idx) == closingChar) {
        builder.result()
      } else {
        while (idx < value.length) {
          // Parse Key
          val n = ParseUtils.skipCQLValue(value, idx)
          val k = keyInner.parse(value.substring(idx, n))
          idx = ParseUtils.skipSpaces(value, n)
          if (idx >= value.length) {
            throw new IllegalArgumentException(
              s"Cannot parse map value from '$value', at character $idx expecting '$keyValueSeparator' but got EOF"
            )
          } else if (value.charAt(idx) != keyValueSeparator) {
            throw new IllegalArgumentException(
              s"Cannot parse map value from '$value', at character $idx expecting '$keyValueSeparator' but got '${value
                  .charAt(idx)}'"
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
              s"Malformed map value '$value', missing closing '$closingChar', but got EOF"
            )
          } else if (value.charAt(idx) == closingChar) {
            return builder.result()
          } else if (value.charAt(idx) != entrySeparator) {
            throw new IllegalArgumentException(
              s"Cannot parse map value from '$value', at character $idx expecting '$entrySeparator' but got '${value
                  .charAt(idx)}''"
            )
          }
          idx = ParseUtils.skipSpaces(value, idx + 1)
        }
        throw new IllegalArgumentException(
          s"Malformed map value '$value', missing closing '$closingChar'"
        )
      }
    }

  override def accepts(value: Any): Boolean = value match {
    case m: Map[_, _] =>
      m.headOption.exists { case (key, value) =>
        keyInner.accepts(key) && valueInner.accepts(value)
      }

    case _ => false
  }
}
