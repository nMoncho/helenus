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

package net.nmoncho.helenus.internal.codec.udt

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType

/** Defines common methods for [[TypeCodec]]s that are implemented for [[UserDefinedType]]
  */
trait UDTCodec[T] { that: TypeCodec[T] =>

  private lazy val userDefinedType = that.getCqlType match {
    case udt: UserDefinedType => udt
    case _ =>
      throw new IllegalArgumentException("UDT Codecs need a UserDefinedType as its CQL Type")
  }

  /** Returns whether the keyspace this [[TypeCodec]] is targeting is empty or not.
    *
    * Note: This happens when users leave the `keyspace` parameter empty, defaulting to Session's Keyspace
    */
  private[helenus] def isKeyspaceBlank: Boolean =
    userDefinedType.getKeyspace.asInternal().isBlank

  /** Wraps this [[TypeCodec]] and points its CQL Type to the provided keyspace.
    *
    * Note: This is useful when the `keyspace` have been left empty, and this codec will be registered. If this codec is
    * not registered, this method it's not necessary.
    */
  private[helenus] def forKeyspace(keyspace: String): TypeCodec[T] = new TypeCodec[T] {
    private val adaptedUserDefinedType = new DefaultUserDefinedType(
      CqlIdentifier.fromInternal(keyspace),
      userDefinedType.getName,
      userDefinedType.isFrozen,
      userDefinedType.getFieldNames,
      userDefinedType.getFieldTypes
    )

    override def getJavaType: GenericType[T] =
      that.getJavaType

    override def getCqlType: DataType = adaptedUserDefinedType

    override def encode(value: T, protocolVersion: ProtocolVersion): ByteBuffer =
      that.encode(value, protocolVersion)

    override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): T =
      that.decode(bytes, protocolVersion)

    override def format(value: T): String =
      that.format(value)

    override def parse(value: String): T =
      that.parse(value)
  }

}
