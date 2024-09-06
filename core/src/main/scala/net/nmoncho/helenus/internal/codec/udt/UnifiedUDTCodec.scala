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
)(implicit tag: ClassTag[A]) extends TypeCodec[A] {

  private var adapted = false

  private[helenus] def adapt(udt: UserDefinedType): Boolean = this.synchronized {

    if(!adapted && !underlying.accepts(udt)) {
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
