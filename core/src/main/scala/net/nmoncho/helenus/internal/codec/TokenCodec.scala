/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.math.BigInteger
import java.nio.ByteBuffer

import scala.util.Try

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token._

sealed trait TokenCodec[T <: Token] extends TypeCodec[T]

object TokenCodec {

  /** [[TypeCodec]] implementation for [[Token]]
    *
    * This implementation is Murmur3-leaning, meaning that token type is expected,
    * and everything else is fallback
    */
  object TokenCodec extends TypeCodec[Token] {

    override def getJavaType: GenericType[Token] = GenericType.of(classOf[Token])

    override def getCqlType: DataType = DataTypes.BIGINT // Murmur3-leaning

    override def encode(value: Token, protocolVersion: ProtocolVersion): ByteBuffer =
      value match {
        case token: Murmur3Token => Murmur3TokenCodec.encode(token, protocolVersion)
        case token: ByteOrderedToken => ByteOrderedTokenCodec.encode(token, protocolVersion)
        case token: RandomToken => RandomTokenCodec.encode(token, protocolVersion)
        case null => null
        case _ => throw new IllegalArgumentException(s"Unsupported token type [${value.getClass}]")
      }

    override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Token =
      if (bytes == null) null
      else
        Try(Murmur3TokenCodec.decode(bytes, protocolVersion))
          .orElse(Try(RandomTokenCodec.decode(bytes, protocolVersion)))
          .orElse(Try(ByteOrderedTokenCodec.decode(bytes, protocolVersion)))
          .getOrElse(
            throw new IllegalArgumentException("Unsupported token type")
          )

    override def format(value: Token): String =
      if (value == null) NULL
      else
        value match {
          case token: Murmur3Token => Murmur3TokenCodec.format(token)
          case token: ByteOrderedToken => ByteOrderedTokenCodec.format(token)
          case token: RandomToken => RandomTokenCodec.format(token)
          case _ =>
            throw new IllegalArgumentException(s"Unsupported token type [${value.getClass}]")
        }

    override def parse(value: String): Token =
      if (value == NULL) null
      else
        Try(Murmur3TokenCodec.parse(value))
          .orElse(Try(RandomTokenCodec.parse(value)))
          .orElse(Try(ByteOrderedTokenCodec.parse(value)))
          .getOrElse(
            throw new IllegalArgumentException(s"Unsupported token type [${value.getClass}]")
          )
  }

  object ByteOrderedTokenCodec
      extends MappingCodec[ByteBuffer, ByteOrderedToken](
        TypeCodecs.BLOB,
        GenericType.of(classOf[ByteOrderedToken])
      )
      with TokenCodec[ByteOrderedToken] {

    override def innerToOuter(value: ByteBuffer): ByteOrderedToken = new ByteOrderedToken(value)

    override def outerToInner(value: ByteOrderedToken): ByteBuffer = value.getValue

  }

  object Murmur3TokenCodec
      extends MappingCodec[java.lang.Long, Murmur3Token](
        TypeCodecs.BIGINT,
        GenericType.of(classOf[Murmur3Token])
      )
      with TokenCodec[Murmur3Token] {

    override def innerToOuter(value: java.lang.Long): Murmur3Token = new Murmur3Token(value)

    override def outerToInner(value: Murmur3Token): java.lang.Long = value.getValue

  }

  object RandomTokenCodec
      extends MappingCodec[BigInteger, RandomToken](
        TypeCodecs.VARINT,
        GenericType.of(classOf[RandomToken])
      )
      with TokenCodec[RandomToken] {

    override def innerToOuter(value: BigInteger): RandomToken = new RandomToken(value)

    override def outerToInner(value: RandomToken): BigInteger = value.getValue

  }

}
