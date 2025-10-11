/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.`type`.codec

import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.collection.{ mutable => mutablecoll }

import com.datastax.dse.driver.api.core.data.geometry.LineString
import com.datastax.dse.driver.api.core.data.geometry.Point
import com.datastax.dse.driver.api.core.data.geometry.Polygon
import com.datastax.dse.driver.api.core.data.time.DateRange
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token.ByteOrderedToken
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.RandomToken
import net.nmoncho.helenus.internal.codec._
import net.nmoncho.helenus.internal.codec.collection._
import net.nmoncho.helenus.internal.codec.enums.EnumerationNominalCodec
import net.nmoncho.helenus.internal.codec.enums.EnumerationOrdinalCodec

/** Constants and factory methods to obtain instances of the scala's default type codecs.
  */
object TypeCodecs {

  /** The default codec that maps CQL type Decimal to Scala's [[BigDecimal]] */
  val bigDecimalCodec: TypeCodec[BigDecimal] = BigDecimalCodec

  /** The default codec that maps CQL type BigInt to Scala's [[BigInt]] */
  val bigIntCodec: TypeCodec[BigInt] = BigIntCodec

  /** The default codec that maps CQL type Boolean to Scala's [[Boolean]] */
  val booleanCodec: TypeCodec[Boolean] = BooleanCodec

  /** The default codec that maps CQL type TinyInt to Scala's [[BigInt]] */
  val byteCodec: TypeCodec[Byte] = ByteCodec

  /** The default codec that maps CQL type Double to Scala's [[Double]] */
  val doubleCodec: TypeCodec[Double] = DoubleCodec

  /** The default codec that maps CQL type Float to Scala's [[Float]] */
  val floatCodec: TypeCodec[Float] = FloatCodec

  /** The default codec that maps CQL type Int to Scala's [[Int]] */
  val intCodec: TypeCodec[Int] = IntCodec

  /** The default codec that maps CQL type BigInt to Scala's [[Long]] */
  val longCodec: TypeCodec[Long] = LongCodec

  /** The default codec that maps CQL type SmallInt to Scala's [[Short]] */
  val shortCodec: TypeCodec[Short] = ShortCodec

  /** The default codec that maps CQL type Text to Scala's [[String]] */
  val stringCodec: TypeCodec[String] = com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.TEXT

  /** The default codec that maps CQL type uuid to [[UUID]] */
  val uuidCodec: TypeCodec[UUID] = com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.UUID

  /** The default codec that maps CQL type Timestamp to [[Instant]] */
  val instantCodec: TypeCodec[Instant] =
    com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.TIMESTAMP

  /** The default codec that maps CQL type Date to [[LocalDate]] */
  val localDateCodec: TypeCodec[LocalDate] =
    com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.DATE

  /** The default codec that maps CQL type Time to [[LocalTime]] */
  val localTimeCodec: TypeCodec[LocalTime] =
    com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.TIME

  /** The default codec that maps CQL type Blob to [[ByteBuffer]] */
  val byteBufferCodec: TypeCodec[ByteBuffer] =
    com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.BLOB

  /** The default codec that maps CQL type Inet to [[java.net.InetAddress]] */
  val inetAddressCodec: TypeCodec[InetAddress] =
    com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs.INET

  // DSE TypeCodecs
  val lineStringCodec: TypeCodec[LineString] =
    com.datastax.dse.driver.api.core.`type`.codec.DseTypeCodecs.LINE_STRING

  val pointCodec: TypeCodec[Point] =
    com.datastax.dse.driver.api.core.`type`.codec.DseTypeCodecs.POINT

  val polygonCodec: TypeCodec[Polygon] =
    com.datastax.dse.driver.api.core.`type`.codec.DseTypeCodecs.POLYGON

  val dateRangeCodec: TypeCodec[DateRange] =
    com.datastax.dse.driver.api.core.`type`.codec.DseTypeCodecs.DATE_RANGE

  // Token TypeCodecs
  val tokenCodec: TypeCodec[Token] = TokenCodec.TokenCodec

  val murmur3TokenCodec: TypeCodec[Murmur3Token] = TokenCodec.Murmur3TokenCodec

  val randomTokenCodec: TypeCodec[RandomToken] = TokenCodec.RandomTokenCodec

  val byteOrderedTokenCodec: TypeCodec[ByteOrderedToken] = TokenCodec.ByteOrderedTokenCodec

  /** Builds a new codec for an [[Enumeration]] by name */
  def enumerationNominalCodec[T <: Enumeration](enumeration: T): TypeCodec[T#Value] =
    new EnumerationNominalCodec[T](enumeration)

  /** Builds a new codec for an [[Enumeration]] by order */
  def enumerationOrdinalCodec[T <: Enumeration](enumeration: T): TypeCodec[T#Value] =
    new EnumerationOrdinalCodec[T](enumeration)

  /** Builds a new codec that wraps another codec's type into [[Option]] instances
    * (mapping CQL null to [[None]]).
    */
  def optionOf[T](inner: TypeCodec[T]): TypeCodec[Option[T]] = OptionCodec(inner)

  /** Builds a new codec that wraps two other codecs type into [[Either]] instances.
    */
  def eitherOf[A, B](left: TypeCodec[A], right: TypeCodec[B]): TypeCodec[Either[A, B]] =
    EitherCodec(left, right)

  /** Builds a new codec that maps a CQL list to a Scala Seq, using the given codec to map each
    * element.
    */
  def seqOf[T](inner: TypeCodec[T]): TypeCodec[Seq[T]] = SeqCodec.frozen(inner)

  /** Builds a new codec that maps a CQL list to a Scala List, using the given codec to map each
    * element.
    */
  def listOf[T](inner: TypeCodec[T]): TypeCodec[List[T]] = ListCodec.frozen(inner)

  /** Builds a new codec that maps a CQL list to a Scala mutable buffer, using the given codec to map each
    * element.
    */
  def mutableBufferOf[T](inner: TypeCodec[T]): TypeCodec[mutablecoll.Buffer[T]] =
    mutable.BufferCodec.frozen(inner)

  /** Builds a new codec that maps a CQL list to a Scala mutable IndexedSeq, using the given codec to map each
    * element.
    */
  def mutableIndexedSeqOf[T](inner: TypeCodec[T]): TypeCodec[mutablecoll.IndexedSeq[T]] =
    mutable.IndexedSeqCodec.frozen(inner)

  /** Builds a new codec that maps a CQL map to a Scala mutable Map, using the given codecs to map each key
    * and value.
    */
  def mutableMapOf[K, V](
      keyInner: TypeCodec[K],
      valueInner: TypeCodec[V]
  ): TypeCodec[mutablecoll.Map[K, V]] =
    mutable.MapCodec.frozen(keyInner, valueInner)

  /** Builds a new codec that maps a CQL set to a Scala mutable Set, using the given codec to map each
    * element.
    */
  def mutableSetOf[T](inner: TypeCodec[T]): TypeCodec[mutablecoll.Set[T]] =
    mutable.SetCodec.frozen(inner)

  /** Builds a new codec that maps a CQL list to a Scala Vector, using the given codec to map each
    * element.
    */
  def vectorOf[T](inner: TypeCodec[T]): TypeCodec[Vector[T]] = VectorCodec.frozen(inner)

  /** Builds a new codec that maps a CQL set to a Scala Set, using the given codec to map each
    * element.
    */
  def setOf[T](inner: TypeCodec[T]): TypeCodec[Set[T]] = SetCodec.frozen(inner)

  /** Builds a new codec that maps a CQL set to a Scala SortedSet, using the given codec to map each
    * element.
    */
  def sortedSetOf[T: Ordering](inner: TypeCodec[T]): TypeCodec[SortedSet[T]] =
    SortedSetCodec.frozen(inner)

  /** Builds a new codec that maps a CQL map to a Scala Map, using the given codecs to map each key
    * and value.
    */
  def mapOf[K, V](keyInner: TypeCodec[K], valueInner: TypeCodec[V]): TypeCodec[Map[K, V]] =
    MapCodec.frozen(keyInner, valueInner)

  /** Builds a new codec that maps a CQL map to a Scala sorted map, using the given codecs to map each key
    * and value.
    */
  def sorterMapOf[K: Ordering, V](
      keyInner: TypeCodec[K],
      valueInner: TypeCodec[V]
  ): TypeCodec[SortedMap[K, V]] =
    SortedMapCodec.frozen(keyInner, valueInner)

}
