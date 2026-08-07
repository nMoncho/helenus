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
import com.datastax.oss.driver.internal.core.`type`.DefaultListType
import com.datastax.oss.driver.internal.core.`type`.DefaultSetType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

abstract class AbstractSeqCodec[T, M[T] <: scala.collection.Seq[T]](
    inner: TypeCodec[T],
    frozen: Boolean
)(implicit factory: Factory[T, M[T]])
    extends IterableCodec[T, M](inner, '[', ']') {

  override val getCqlType: DataType = new DefaultListType(inner.getCqlType, frozen)

  override protected def acceptsCollection(value: Any): Boolean =
    value.isInstanceOf[scala.collection.Seq[_]]
}

abstract class AbstractSetCodec[T, M[T] <: scala.collection.Set[T]](
    inner: TypeCodec[T],
    frozen: Boolean
)(implicit factory: Factory[T, M[T]])
    extends IterableCodec[T, M](inner, '{', '}') {

  override val getCqlType: DataType = new DefaultSetType(inner.getCqlType, frozen)

  override protected def acceptsCollection(value: Any): Boolean =
    value.isInstanceOf[scala.collection.Set[_]]
}

abstract class IterableCodec[T, M[T] <: Iterable[T]](
    inner: TypeCodec[T],
    openingChar: Char,
    closingChar: Char
)(
    implicit factory: Factory[T, M[T]]
) extends TypeCodec[M[T]] {

  private val separator: Char = ','

  override def encode(value: M[T], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      // Pre-size a plain array (matches the Java driver) instead of a ListBuffer,
      // so there is no per-element cons-cell allocation.
      val elements   = new Array[ByteBuffer](value.size)
      var toAllocate = 4 // 4 bytes for the element count prefix
      var count      = 0

      // Iterate via the iterator + while loop to avoid the closure that a
      // `for (item <- value)` (i.e. foreach) allocates.
      val it = value.iterator
      while (it.hasNext) {
        val item = it.next()
        if (item == null) {
          throw new IllegalArgumentException("Collection elements cannot be null")
        }

        val element = inner.encode(item, protocolVersion)
        if (element == null) {
          throw new NullPointerException("Collection elements cannot encode to CQL NULL")
        }

        elements(count) = element
        toAllocate += 4 + element.remaining()
        count += 1
      }

      val result = ByteBuffer.allocate(toAllocate)
      result.putInt(count)

      var j = 0
      while (j < count) {
        val element = elements(j)
        // Full 32-bit length prefix: the previous `.toShort` truncated any
        // element larger than 32767 bytes and corrupted the payload.
        result.putInt(element.remaining())
        // No defensive `.duplicate()`: `inner.encode` returns a fresh, single-use
        // buffer with exactly one consumer (see the codec package invariant).
        result.put(element)
        j += 1
      }

      result.flip()
      result // return the buffer explicitly; safe across JDK 8 (flip returns Buffer) and 9+
    }

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): M[T] = {
    val builder = factory.newBuilder

    if (bytes == null || bytes.remaining == 0) builder.result()
    else {
      val input = bytes.duplicate()
      val size  = input.getInt()
      builder.sizeHint(size) // avoid the builder growing/recopying its backing store

      var i = 0
      while (i < size) {
        val elementSize = input.getInt()

        val element =
          if (elementSize < 0) null
          else {
            val copy = input.duplicate()
            copy.limit(copy.position() + elementSize)
            input.position(input.position() + elementSize)

            copy
          }

        builder += inner.decode(element, protocolVersion)
        i += 1
      }

      builder.result()
    }
  }

  override def format(value: M[T]): String =
    if (value == null) {
      NULL
    } else {
      val sb   = new mutablecoll.StringBuilder().append(openingChar)
      var tail = false
      val it   = value.iterator
      while (it.hasNext) {
        if (tail) sb.append(separator)
        else tail = true

        sb.append(inner.format(it.next()))
      }
      sb.append(closingChar).toString()
    }

  @SuppressWarnings(Array("DisableSyntax.return"))
  override def parse(value: String): M[T] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) {
      null.asInstanceOf[M[T]]
    } else {
      val builder = factory.newBuilder
      var idx     = skipSpacesAndExpect(value, 0, openingChar)

      if (value.charAt(idx) == closingChar) {
        builder.result()
      } else {
        while (idx < value.length) {
          val (element, n) = parseWithCodec(value, inner, idx)

          builder += element

          idx = ParseUtils.skipSpaces(value, n)
          if (isParseFinished(value, idx, closingChar, separator)) {
            return builder.result()
          }

          idx = ParseUtils.skipSpaces(value, idx + 1)
        }

        throw new IllegalArgumentException(
          s"Malformed collection value '$value', missing closing '$closingChar'"
        )
      }
    }

  /** Whether `value`'s runtime collection type belongs to this codec's family
    * (a `Seq` vs a `Set`).
    *
    * The element type `M` is erased, so a bare `value match { case _: M[_] }`
    * degrades to `case _: Iterable[_]` and cannot tell a `Seq` from a `Set`.
    * Concrete codecs implement this against the concrete collection trait so a
    * `Seq` codec rejects a `Set` (and vice versa).
    */
  protected def acceptsCollection(value: Any): Boolean

  override def accepts(value: Any): Boolean = value match {
    case iterable: Iterable[_] if acceptsCollection(value) =>
      // Under erasure only the head element's type can be sampled. An empty
      // collection of the right family is accepted since there is nothing to
      // check. Value-based registry lookup cannot disambiguate same-family
      // codecs (e.g. List vs Vector) and is not supported for these codecs.
      iterable.isEmpty || inner.accepts(iterable.head)

    case _ => false
  }
}
