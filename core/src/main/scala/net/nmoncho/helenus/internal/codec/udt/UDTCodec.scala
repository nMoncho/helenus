/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.udt

import java.nio.ByteBuffer

import scala.jdk.OptionConverters.RichOptional

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
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

  private[helenus] def existsInKeyspace(session: CqlSession): Boolean = (for {
    keyspaceName <- session.getKeyspace.toScala
    keyspace <- session.getMetadata.getKeyspace(keyspaceName).toScala
    udt <- keyspace.getUserDefinedType(userDefinedType.getName).toScala
  } yield udt).nonEmpty

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
