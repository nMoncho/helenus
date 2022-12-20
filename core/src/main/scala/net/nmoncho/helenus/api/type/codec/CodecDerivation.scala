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

package net.nmoncho.helenus.api.`type`.codec

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.{ MappingCodec, TypeCodec }
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import net.nmoncho.helenus.api.{
  ColumnNamingScheme,
  DefaultColumnNamingScheme,
  NominalEncoded,
  OrdinalEncoded,
  Udt
}
import net.nmoncho.helenus.internal.codec.{ TupleCodecDerivation, UdtCodecDerivation }
import shapeless.{ <:!<, Annotation, IsTuple, Witness }

import java.net.InetAddress
import java.time.{ Instant, LocalDate, LocalTime }
import java.util.UUID
import scala.annotation.implicitNotFound
import scala.collection.immutable.{ SortedMap, SortedSet }
import scala.reflect.ClassTag

trait CodecDerivation extends TupleCodecDerivation with UdtCodecDerivation { that =>

  implicit final val bigDecimalCodec: TypeCodec[BigDecimal] = TypeCodecs.bigDecimalCodec

  implicit final val bigIntCodec: TypeCodec[BigInt] = TypeCodecs.bigIntCodec

  implicit final val booleanCodec: TypeCodec[Boolean] = TypeCodecs.booleanCodec

  implicit final val byteCodec: TypeCodec[Byte] = TypeCodecs.byteCodec

  implicit final val doubleCodec: TypeCodec[Double] = TypeCodecs.doubleCodec

  implicit final val floatCodec: TypeCodec[Float] = TypeCodecs.floatCodec

  implicit final val intCodec: TypeCodec[Int] = TypeCodecs.intCodec

  implicit final val longCodec: TypeCodec[Long] = TypeCodecs.longCodec

  implicit final val shortCodec: TypeCodec[Short] = TypeCodecs.shortCodec

  implicit final val stringCodec: TypeCodec[String] = TypeCodecs.stringCodec

  implicit final val uuidCodec: TypeCodec[UUID] = TypeCodecs.uuidCodec

  implicit final val instantCodec: TypeCodec[Instant] = TypeCodecs.instantCodec

  implicit final val localDateCodec: TypeCodec[LocalDate] = TypeCodecs.localDateCodec

  implicit final val localTimeCodec: TypeCodec[LocalTime] = TypeCodecs.localTimeCodec

  implicit final val inetAddressCodec: TypeCodec[InetAddress] = TypeCodecs.inetAddressCodec

  implicit def enumNominalCodec[T <: Enumeration](
      implicit w: Witness.Aux[T],
      annotation: Annotation[NominalEncoded, T]
  ): TypeCodec[T#Value] =
    TypeCodecs.enumerationNominalCodec(w.value)

  implicit def enumOrdinalCodec[T <: Enumeration](
      implicit w: Witness.Aux[T],
      annotation: Annotation[OrdinalEncoded, T]
  ): TypeCodec[T#Value] =
    TypeCodecs.enumerationOrdinalCodec(w.value)

  implicit def optionCodec[T: TypeCodec]: TypeCodec[Option[T]] =
    TypeCodecs.optionOf(implicitly[TypeCodec[T]])

  implicit def eitherCodec[A: TypeCodec, B: TypeCodec]: TypeCodec[Either[A, B]] =
    TypeCodecs.eitherOf(implicitly[TypeCodec[A]], implicitly[TypeCodec[B]])

  implicit def seqOf[T: TypeCodec]: TypeCodec[Seq[T]] =
    TypeCodecs.seqOf(implicitly[TypeCodec[T]])

  implicit def listOf[T: TypeCodec]: TypeCodec[List[T]] =
    TypeCodecs.listOf(implicitly[TypeCodec[T]])

  implicit def vectorOf[T: TypeCodec]: TypeCodec[Vector[T]] =
    TypeCodecs.vectorOf(implicitly[TypeCodec[T]])

  implicit def setOf[T: TypeCodec]: TypeCodec[Set[T]] =
    TypeCodecs.setOf(implicitly[TypeCodec[T]])

  implicit def sortedSetOf[T: TypeCodec: Ordering]: TypeCodec[SortedSet[T]] =
    TypeCodecs.sortedSetOf(implicitly[TypeCodec[T]])

  implicit def mapOf[K: TypeCodec, V: TypeCodec]: TypeCodec[Map[K, V]] =
    TypeCodecs.mapOf(implicitly[TypeCodec[K]], implicitly[TypeCodec[V]])

  implicit def sorterMapOf[K: TypeCodec: Ordering, V: TypeCodec]: TypeCodec[SortedMap[K, V]] =
    TypeCodecs.sorterMapOf(implicitly[TypeCodec[K]], implicitly[TypeCodec[V]])

  /** Derives a [[ColumnMapper]] for a given type [[A]], if it's not a [[Product]] (ie. tuple or case class)
    */
  implicit def columnMapper[A](
      implicit ev: A <:!< Product,
      codec: TypeCodec[A]
  ): ColumnMapper[A] =
    ColumnMapper.default[A]

  /** Derives a [[ColumnMapper]] for a given type [[A]], if it's a tuple
    */
  implicit def columnMapperForTuple[A](
      implicit ev: IsTuple[A],
      codec: TypeCodec[A]
  ): ColumnMapper[A] =
    ColumnMapper.default[A]

  object Codec {

    /** Creates a new mapping codec providing support for [[Outer]] based on an existing codec for [[Inner]].
      *
      * @param toOuter how to map from [[Inner]] to [[Outer]].
      * @param toInner how to map from [[Outer]] to [[Inner]].
      * @param codec The inner codec to use to handle instances of [[Inner]]; must not be null.
      * @param tag [[Outer]] ClassTag
      */
    def mappingCodec[Inner, Outer](
        toOuter: Inner => Outer,
        toInner: Outer => Inner
    )(implicit codec: TypeCodec[Inner], tag: ClassTag[Outer]): TypeCodec[Outer] =
      new MappingCodec[Inner, Outer](
        codec,
        GenericType.of(tag.runtimeClass.asInstanceOf[Class[Outer]])
      ) {
        override def innerToOuter(value: Inner): Outer = toOuter(value)

        override def outerToInner(value: Outer): Inner = toInner(value)
      }

    def udtOf[T <: Product with Serializable: ClassTag: UdtCodec](
        implicit annotation: Annotation[Udt, T],
        columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = that.udtOf[T]

    def udtFrom[T <: Product with Serializable: ClassTag: UdtCodec](session: CqlSession)(
        implicit annotation: Annotation[Udt, T],
        columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = that.udtFrom[T](session)

    def tupleOf[T: IsTuple](implicit tupleCodec: TupleCodec[T]): TypeCodec[T] =
      that.tupleOf[T]

    /** Summoner method for a [[Codec]] of type [[T]] */
    def apply[T](
        implicit @implicitNotFound(
          "If ${T} is a simple type, then it isn't defined in `net.nmoncho.helenus`. " +
            "If ${T} is a UDT/Case Class, it must tagged with @Udt. " +
            "If ${T} is an Enumeration, it must tagged with some enum annotation. " +
            "Also make sure you aren't shadowing any implicit definition"
        )
        codec: TypeCodec[T]
    ): TypeCodec[T] = codec
  }

}
