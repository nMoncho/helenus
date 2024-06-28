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

package net.nmoncho.helenus.flink.typeinfo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import scala.collection.mutable
import scala.reflect.ClassTag

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.tuple

object Types {

  val Void: TypeInformation[Void] = org.apache.flink.api.common.typeinfo.Types.VOID

  val Unit: TypeInformation[Unit] = UnitTypeInformation

  val String: TypeInformation[String] =
    org.apache.flink.api.common.typeinfo.Types.STRING

  val JavaByte: TypeInformation[java.lang.Byte] =
    org.apache.flink.api.common.typeinfo.Types.BYTE

  val Byte: TypeInformation[Byte] =
    JavaByte.asInstanceOf[TypeInformation[Byte]]

  val JavaBoolean: TypeInformation[java.lang.Boolean] =
    org.apache.flink.api.common.typeinfo.Types.BOOLEAN

  val Boolean: TypeInformation[Boolean] =
    JavaBoolean.asInstanceOf[TypeInformation[Boolean]]

  val JavaShort: TypeInformation[java.lang.Short] =
    org.apache.flink.api.common.typeinfo.Types.SHORT

  val Short: TypeInformation[Short] =
    JavaShort.asInstanceOf[TypeInformation[Short]]

  val JavaInteger: TypeInformation[Integer] =
    org.apache.flink.api.common.typeinfo.Types.INT

  val Int: TypeInformation[Int] =
    JavaInteger.asInstanceOf[TypeInformation[Int]]

  val JavaLong: TypeInformation[java.lang.Long] =
    org.apache.flink.api.common.typeinfo.Types.LONG

  val Long: TypeInformation[Long] =
    JavaLong.asInstanceOf[TypeInformation[Long]]

  val JavaFloat: TypeInformation[java.lang.Float] =
    org.apache.flink.api.common.typeinfo.Types.FLOAT

  val Float: TypeInformation[Float] =
    JavaFloat.asInstanceOf[TypeInformation[Float]]

  val JavaDouble: TypeInformation[java.lang.Double] =
    org.apache.flink.api.common.typeinfo.Types.DOUBLE

  val Double: TypeInformation[Double] =
    JavaDouble.asInstanceOf[TypeInformation[Double]]

  val JavaCharacter: TypeInformation[Character] =
    org.apache.flink.api.common.typeinfo.Types.CHAR

  val Char: TypeInformation[Char] =
    JavaCharacter.asInstanceOf[TypeInformation[Char]]

  def JavaEnum[T <: Enum[T]](tag: ClassTag[T]): TypeInformation[T] =
    org.apache.flink.api.common.typeinfo.Types.ENUM(tag.runtimeClass.asInstanceOf[Class[T]])

  val Instant: TypeInformation[Instant] =
    org.apache.flink.api.common.typeinfo.Types.INSTANT

  val LocalDate: TypeInformation[LocalDate] =
    org.apache.flink.api.common.typeinfo.Types.LOCAL_DATE

  val LocalTime: TypeInformation[LocalTime] =
    org.apache.flink.api.common.typeinfo.Types.LOCAL_TIME

  val LocalDateTime: TypeInformation[LocalDateTime] =
    org.apache.flink.api.common.typeinfo.Types.LOCAL_DATE_TIME

  def FlinkTuple[T <: tuple.Tuple](tag: ClassTag[T]): TypeInformation[T] =
    org.apache.flink.api.common.typeinfo.Types.TUPLE(tag.runtimeClass.asInstanceOf[Class[T]])

  def FlinkEither[L, R](
      left: TypeInformation[L],
      right: TypeInformation[R]
  ): TypeInformation[org.apache.flink.types.Either[L, R]] =
    org.apache.flink.api.common.typeinfo.Types.EITHER(left, right)

  def PrimitiveArray[T <: AnyVal](
      inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    org.apache.flink.api.common.typeinfo.Types
      .PRIMITIVE_ARRAY(inner)
      .asInstanceOf[TypeInformation[Array[T]]]

  def ObjectArray[T <: AnyRef](
      inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    org.apache.flink.api.common.typeinfo.Types.OBJECT_ARRAY[T](inner)

  def JavaMap[K, V](
      key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[java.util.Map[K, V]] =
    org.apache.flink.api.common.typeinfo.Types.MAP(key, value)

  def JavaList[T](
      inner: TypeInformation[T]
  ): TypeInformation[java.util.List[T]] =
    org.apache.flink.api.common.typeinfo.Types.LIST(inner)

  def List[T](
      inner: TypeInformation[T]
  ): TypeInformation[List[T]] =
    new collection.immutable.ListTypeInformation[T](inner)

  def Map[K, V](
      key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[Map[K, V]] =
    new collection.immutable.MapTypeInformation[K, V](key, value)

  def Seq[T](
      inner: TypeInformation[T]
  ): TypeInformation[Seq[T]] =
    new collection.immutable.SeqTypeInformation[T](inner)

  def Set[T](
      inner: TypeInformation[T]
  ): TypeInformation[Set[T]] =
    new collection.immutable.SetTypeInformation[T](inner)

  def Vector[T](
      inner: TypeInformation[T]
  ): TypeInformation[Vector[T]] =
    new collection.immutable.VectorTypeInformation[T](inner)

  def Buffer[T](
      inner: TypeInformation[T]
  ): TypeInformation[mutable.Buffer[T]] =
    new collection.mutable.BufferTypeInformation[T](inner)

  def MutableIndexedSeq[T](
      inner: TypeInformation[T]
  ): TypeInformation[mutable.IndexedSeq[T]] =
    new collection.mutable.IndexedSeqTypeInformation[T](inner)

  def MutableMap[K, V](
      key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[mutable.Map[K, V]] =
    new collection.mutable.MapTypeInformation[K, V](key, value)

  def MutableSet[T](
      inner: TypeInformation[T]
  ): TypeInformation[mutable.Set[T]] =
    new collection.mutable.SetTypeInformation[T](inner)
}
