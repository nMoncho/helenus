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
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.{ GenericType, GenericTypeParameter }
import com.datastax.oss.driver.internal.core.`type`.DefaultTupleType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

import java.nio.ByteBuffer

/** [[TypeCodec]] implementation for [[Either]]. Translates to a two element tuple in Cassandra.
  * Another design possibility would be to use a UDT, but that would require users to configure a custom type.
  *
  * @param left left codec
  * @param right right codec
  */
class EitherCodec[A, B](left: TypeCodec[A], right: TypeCodec[B]) extends TypeCodec[Either[A, B]] {

  override def encode(value: Either[A, B], protocolVersion: ProtocolVersion): ByteBuffer =
    if (value == null) null
    else {
      val buffer = value.fold(left.encode(_, protocolVersion), right.encode(_, protocolVersion))
      val size   = buffer.remaining()
      val result = ByteBuffer.allocate(8 + size)

      // Encoding tuples means putting each element's size first
      if (value.isLeft) {
        result.putInt(size).put(buffer.duplicate()).putInt(-1)
      } else {
        result.putInt(-1).putInt(size).put(buffer.duplicate())
      }

      result.flip()
    }

  override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): Either[A, B] =
    if (buffer == null) null.asInstanceOf[Either[A, B]]
    else {
      val input = buffer.duplicate()

      // If first element has size, then it's a `Left`, otherwise is a `Right`
      val elementSize = input.getInt
      if (elementSize >= 0) {
        val element = input.slice()
        element.limit(elementSize)

        Left(left.decode(element, protocolVersion))
      } else {
        val elementSize = input.getInt
        val element     = input.slice()
        element.limit(elementSize)

        Right(right.decode(element, protocolVersion))
      }
    }

  override val getJavaType: GenericType[Either[A, B]] =
    GenericType
      .of(new TypeToken[Either[A, B]]() {}.getType)
      .where(new GenericTypeParameter[A] {}, left.getJavaType.wrap())
      .where(new GenericTypeParameter[B] {}, right.getJavaType.wrap())
      .asInstanceOf[GenericType[Either[A, B]]]

  override val getCqlType: DataType = new DefaultTupleType(
    java.util.List.of(left.getCqlType, right.getCqlType)
  )

  override def format(value: Either[A, B]): String =
    if (value == null) "NULL"
    else {
      value.fold(
        l => s"(${left.format(l)},NULL)",
        r => s"(NULL,${right.format(r)})"
      )
    }

  override def parse(value: String): Either[A, B] =
    if (value == null || value.isEmpty || value.equalsIgnoreCase("NULL")) {
      null.asInstanceOf[Either[A, B]]
    } else {
      var idx = ParseUtils.skipSpaces(value, 0)

      if (idx >= value.length) {
        throw new IllegalArgumentException(
          s"Cannot parse either value from '$value', expecting '(', but got EOF"
        )
      } else if (value.charAt(idx) != '(') {
        throw new IllegalArgumentException(
          s"Cannot parse either value from '$value', at character $idx expecting '(' but got '${value
              .charAt(idx)}''"
        )
      }
      idx = ParseUtils.skipSpaces(value, idx + 1)

      val leftEndIdx    = ParseUtils.skipCQLValue(value, idx)
      val leftSubstring = value.substring(idx, leftEndIdx)
      val leftValue =
        if (leftSubstring.equalsIgnoreCase("NULL"))
          null.asInstanceOf[A] // need to do this due to `AnyVal` types not returning null
        else left.parse(leftSubstring)

      idx = ParseUtils.skipSpaces(value, leftEndIdx)
      if (idx >= value.length) {
        throw new IllegalArgumentException(
          s"Cannot parse either value from '$value', expecting ',', but got EOF"
        )
      } else if (value.charAt(idx) != ',') {
        throw new IllegalArgumentException(
          s"Cannot parse either value from '$value', at character $idx expecting ',' but got '${value
              .charAt(idx)}'"
        )
      }
      idx = ParseUtils.skipSpaces(value, idx + 1)

      val rightEndIdx = ParseUtils.skipCQLValue(value, idx)
      val rightValue  = right.parse(value.substring(idx, rightEndIdx))

      idx = ParseUtils.skipSpaces(value, rightEndIdx)
      if (idx >= value.length) {
        throw new IllegalArgumentException(
          s"Cannot parse either value from '$value', expecting ')', but got EOF"
        )
      } else if (value.charAt(idx) != ')') {
        throw new IllegalArgumentException(
          s"Malformed either value '$value', expected closing ')' but got '${value.charAt(idx)}'"
        )
      }

      if (leftValue != null) Left(leftValue) else Right(rightValue)
    }

  override def accepts(value: Any): Boolean = value match {
    case Left(l) => left.accepts(l)
    case Right(r) => right.accepts(r)
    case _ => false
  }

}

object EitherCodec {
  def apply[A, B](left: TypeCodec[A], right: TypeCodec[B]): TypeCodec[Either[A, B]] =
    new EitherCodec(left, right)
}
