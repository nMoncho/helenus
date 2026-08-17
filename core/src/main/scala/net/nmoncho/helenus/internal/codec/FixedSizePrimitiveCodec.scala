/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import java.nio.ByteBuffer

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

/** Shared implementation for the fixed-size primitive codecs (`Int`, `Long`, `Byte`, `Short`,
  * `Double`, `Float`, and `Boolean`).
  *
  * These codecs used to be near-identical copies. This base keeps the parts that must stay
  * consistent across all of them, the null-to-default decode policy, the wrong-size guard, `format`,
  * the `parse` null/`NULL`/error handling, `getJavaType`, and the class/type `accepts` checks, in one
  * place. A concrete codec only supplies the CQL type, byte width, and the type-specific
  * [[encode]] / [[readValue]] / [[parseValue]] / [[defaultValue]] pieces (plus the boxing-aware
  * value-based `accepts`, which cannot be expressed generically over an erased primitive).
  *
  * @param getCqlType      the CQL type this codec maps to
  * @param byteSize        the fixed serialized width, in bytes
  * @param typeDescription human-readable type name, used in error messages
  */
abstract class FixedSizePrimitiveCodec[T](
    val getCqlType: DataType,
    protected val byteSize: Int,
    protected val typeDescription: String
)(implicit classTag: ClassTag[T])
    extends TypeCodec[T] {

  /** Value returned for a null/empty buffer, or a null/empty/`NULL` string. */
  protected def defaultValue: T

  /** Reads the value from a buffer already checked to be non-null and of the right size.
    * Must not advance the buffer's position (use absolute reads).
    */
  protected def readValue(bytes: ByteBuffer): T

  /** Parses a string already checked to be non-null, non-empty, and not `NULL`. */
  protected def parseValue(value: String): T

  final val getJavaType: GenericType[T] =
    GenericType.of(classTag.runtimeClass.asInstanceOf[Class[T]])

  final def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): T =
    if (bytes == null || bytes.remaining == 0) defaultValue
    else if (bytes.remaining != byteSize)
      throw new IllegalArgumentException(
        s"Invalid $typeDescription value, expecting $byteSize ${if (byteSize == 1) "byte"
          else "bytes"} but got [${bytes.remaining}]"
      )
    else readValue(bytes)

  final def format(value: T): String =
    value.toString

  final def parse(value: String): T =
    if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) defaultValue
    else
      try parseValue(value)
      catch {
        case e: IllegalArgumentException =>
          throw new IllegalArgumentException(
            s"Cannot parse $typeDescription value from [$value]",
            e
          )
      }

  override def accepts(javaClass: Class[_]): Boolean = javaClass == classTag.runtimeClass

  override def accepts(javaType: GenericType[_]): Boolean = javaType == getJavaType

}
