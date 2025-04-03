/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.udt

import java.nio.ByteBuffer

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import org.slf4j.LoggerFactory

class UnifiedUDTCodec[A <: Product](
    @volatile private var underlying: TypeCodec[A],
    mappingCodec: UserDefinedType => TypeCodec[A]
)(implicit tag: ClassTag[A])
    extends TypeCodec[A]
    with UDTCodec[A] {

  private var adapted = false

  private[helenus] def adapt(udt: UserDefinedType): Boolean = this.synchronized {

    if (!adapted && !underlying.accepts(udt)) {
      UnifiedUDTCodec.log.info(
        "Adapting UDT Codec for class [{}] since an IdenticalUDTCodec doesn't provide the same field order",
        tag.runtimeClass.getCanonicalName()
      )
      adapted    = true
      underlying = mappingCodec(udt)
    }

    underlying.accepts(udt)
  }

  override def getCqlType(): DataType = underlying.getCqlType()

  override def getJavaType(): GenericType[A] = underlying.getJavaType()

  override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): A =
    underlying.decode(bytes, protocolVersion)

  override def encode(value: A, protocolVersion: ProtocolVersion): ByteBuffer =
    underlying.encode(value, protocolVersion)

  override def format(value: A): String = underlying.format(value)

  override def parse(value: String): A = underlying.parse(value)

  override def accepts(cqlType: DataType): Boolean = underlying.accepts(cqlType)

  override def accepts(javaType: GenericType[?]): Boolean = underlying.accepts(javaType)

  override def accepts(javaClass: Class[?]): Boolean = underlying.accepts(javaClass)

  override def accepts(value: Object): Boolean = underlying.accepts(value)

  override def toString(): String = underlying.toString()
}

object UnifiedUDTCodec {
  private val log = LoggerFactory.getLogger(classOf[UnifiedUDTCodec[_]])
}
