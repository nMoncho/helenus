/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import scala.collection.mutable
import scala.reflect.ClassTag

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.tuple

// $COVERAGE-OFF$
trait ImplicitTypes {

  implicit val voidTypeInfo: TypeInformation[Void] = Types.Void

  implicit val unitTypeInfo: TypeInformation[Unit] = Types.Unit

  implicit val stringTypeInfo: TypeInformation[String] = Types.String

  implicit val javaByteTypeInfo: TypeInformation[java.lang.Byte] = Types.JavaByte

  implicit val byteTypeInfo: TypeInformation[Byte] = Types.Byte

  implicit val javaBooleanTypeInfo: TypeInformation[java.lang.Boolean] = Types.JavaBoolean

  implicit val booleanTypeInfo: TypeInformation[Boolean] = Types.Boolean

  implicit val javaShortTypeInfo: TypeInformation[java.lang.Short] = Types.JavaShort

  implicit val shortTypeInfo: TypeInformation[Short] = Types.Short

  implicit val javaIntTypeInfo: TypeInformation[Integer] = Types.JavaInteger

  implicit val intTypeInfo: TypeInformation[Int] = Types.Int

  implicit val javaLongTypeInfo: TypeInformation[java.lang.Long] = Types.JavaLong

  implicit val longTypeInfo: TypeInformation[Long] = Types.Long

  implicit val javaFloatTypeInfo: TypeInformation[java.lang.Float] = Types.JavaFloat

  implicit val floatTypeInfo: TypeInformation[Float] = Types.Float

  implicit val javaDoubleTypeInfo: TypeInformation[java.lang.Double] = Types.JavaDouble

  implicit val doubleTypeInfo: TypeInformation[Double] = Types.Double

  implicit val javaCharTypeInfo: TypeInformation[Character] = Types.JavaCharacter

  implicit val charTypeInfo: TypeInformation[Char] = Types.Char

  implicit def enumTypeInfo[T <: Enum[T]](implicit tag: ClassTag[T]): TypeInformation[T] =
    Types.JavaEnum(tag)

  implicit val instantTypeInfo: TypeInformation[Instant] = Types.Instant

  implicit val localDateTypeInfo: TypeInformation[LocalDate] = Types.LocalDate

  implicit val localTimeTypeInfo: TypeInformation[LocalTime] = Types.LocalTime

  implicit val localDateTimeTypeInfo: TypeInformation[LocalDateTime] = Types.LocalDateTime

  implicit def flinkTupleTypeInfo[T <: tuple.Tuple](implicit tag: ClassTag[T]): TypeInformation[T] =
    Types.FlinkTuple(tag)

  implicit def flinkEitherTypeInfo[L, R](
      implicit left: TypeInformation[L],
      right: TypeInformation[R]
  ): TypeInformation[org.apache.flink.types.Either[L, R]] =
    Types.FlinkEither(left, right)

  implicit def primitiveArrayTypeInfo[T <: AnyVal](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    Types.PrimitiveArray(inner)

  implicit def objectArrayTypeInfo[T <: AnyRef](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    Types.ObjectArray(inner)

  implicit def javaMapTypeInfo[K, V](
      implicit key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[java.util.Map[K, V]] =
    Types.JavaMap(key, value)

  implicit def javaListTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[java.util.List[T]] =
    Types.JavaList(inner)

  implicit def listTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[List[T]] =
    Types.List(inner)

  implicit def mapTypeInfo[K, V](
      implicit key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[Map[K, V]] =
    Types.Map(key, value)

  implicit def seqTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Seq[T]] =
    Types.Seq(inner)

  implicit def setTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Set[T]] =
    Types.Set(inner)

  implicit def vectorTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Vector[T]] =
    Types.Vector(inner)

  implicit def bufferTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[mutable.Buffer[T]] =
    Types.Buffer(inner)

  implicit def mutableIndexedSeqTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[mutable.IndexedSeq[T]] =
    Types.MutableIndexedSeq(inner)

  implicit def mutableMapTypeInfo[K, V](
      implicit key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[mutable.Map[K, V]] =
    Types.MutableMap(key, value)

  implicit def mutableSetTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[mutable.Set[T]] =
    Types.MutableSet(inner)

}

object ImplicitTypes extends ImplicitTypes
// $COVERAGE-ON$
