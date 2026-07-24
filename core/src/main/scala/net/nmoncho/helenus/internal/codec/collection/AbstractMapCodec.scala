/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec
package collection

import java.nio.ByteBuffer

import scala.collection.compat._
import scala.collection.{ mutable => mutablecoll }

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.DefaultMapType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

abstract class AbstractMapCodec[K, V, M[K, V] <: scala.collection.Map[K, V]](
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
      // Two buffers per entry (key + value). Pre-size a flat array instead of a
      // ListBuffer so there is no per-buffer cons-cell allocation.
      val elements   = new Array[ByteBuffer](value.size * 2)
      var toAllocate = 4 // 4 bytes for the entry count prefix
      var idx        = 0 // write position into `elements`
      var count      = 0 // number of entries actually iterated

      // Iterate via the iterator + while loop to avoid the closure that a
      // `for ((k, v) <- value)` (i.e. foreach) allocates.
      val it = value.iterator
      while (it.hasNext) {
        val (k, v) = it.next()
        if (k == null) {
          throw new IllegalArgumentException("Map keys cannot be null")
        }
        if (v == null) {
          throw new IllegalArgumentException("Map values cannot be null")
        }

        val encodedKey   = keyInner.encode(k, protocolVersion)
        val encodedValue = valueInner.encode(v, protocolVersion)

        if (encodedKey == null) {
          throw new NullPointerException("Map keys cannot encode to CQL NULL")
        } else if (encodedValue == null) {
          throw new NullPointerException("Map values cannot encode to CQL NULL")
        }

        toAllocate += (4 + encodedKey.remaining()) + (4 + encodedValue.remaining())
        elements(idx)     = encodedKey
        elements(idx + 1) = encodedValue
        idx += 2
        count += 1
      }

      val result = ByteBuffer.allocate(toAllocate)
      result.putInt(count)

      var j = 0
      while (j < idx) {
        val element = elements(j)
        result.putInt(element.remaining())
        result.put(element)
        j += 1
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
      builder.sizeHint(size) // avoid the builder growing/recopying its backing store

      var i = 0
      while (i < size) {
        // Allow null elements on the decode path, because Cassandra might return such collections
        // for some computed values in the future -- e.g. SELECT ttl(some_collection)

        // Decode Key
        val keySize = input.getInt()
        val key     =
          if (keySize < 0) null.asInstanceOf[K]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + keySize)
            input.position(input.position() + keySize)
            keyInner.decode(copy, protocolVersion)
          }

        // Decode Value
        val valueSize = input.getInt()
        val value     =
          if (valueSize < 0) null.asInstanceOf[V]
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + valueSize)
            input.position(input.position() + valueSize)

            valueInner.decode(copy, protocolVersion)
          }

        builder += key -> value
        i += 1
      }

      builder.result()
    }
  }

  override def format(map: M[K, V]): String =
    if (map == null) {
      NULL
    } else {
      val sb   = new mutablecoll.StringBuilder().append(openingChar)
      var tail = false
      val it   = map.iterator
      while (it.hasNext) {
        val (key, value) = it.next()
        if (tail) sb.append(entrySeparator)
        else tail = true

        sb.append(keyInner.format(key))
          .append(keyValueSeparator)
          .append(valueInner.format(value))
      }
      sb.append(closingChar).toString()
    }

  @SuppressWarnings(Array("DisableSyntax.return"))
  override def parse(value: String): M[K, V] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) {
      null.asInstanceOf[M[K, V]]
    } else {
      val builder = factory.newBuilder
      var idx     = skipSpacesAndExpect(value, 0, openingChar)

      if (value.charAt(idx) == closingChar) {
        builder.result()
      } else {
        while (idx < value.length) {
          // Parse Key
          val (k, nk) = parseWithCodec(value, keyInner, idx)

          idx = skipSpacesAndExpect(value, nk, keyValueSeparator)

          // Parse Value
          val (v, nv) = parseWithCodec(value, valueInner, idx)

          builder += k -> v

          idx = ParseUtils.skipSpaces(value, nv)
          if (isParseFinished(value, idx, closingChar, entrySeparator)) {
            return builder.result()
          }
          idx = ParseUtils.skipSpaces(value, idx + 1)
        }

        throw new IllegalArgumentException(
          s"Malformed map value '$value', missing closing '$closingChar'"
        )
      }
    }

  override def accepts(value: Any): Boolean = value match {
    case m: scala.collection.Map[_, _] =>
      if (m.isEmpty) false
      else {
        val (key, value) = m.head
        keyInner.accepts(key) && valueInner.accepts(value)
      }

    case _ => false
  }
}
