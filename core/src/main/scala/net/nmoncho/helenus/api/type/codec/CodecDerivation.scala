/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.`type`.codec

import java.net.InetAddress
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

import scala.annotation.implicitNotFound
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.ClassTag

import com.datastax.dse.driver.api.core.data.geometry.LineString
import com.datastax.dse.driver.api.core.data.geometry.Point
import com.datastax.dse.driver.api.core.data.geometry.Polygon
import com.datastax.dse.driver.api.core.data.time.DateRange
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.NominalEncoded
import net.nmoncho.helenus.api.OrdinalEncoded
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import net.nmoncho.helenus.internal.codec.TupleCodecDerivation
import net.nmoncho.helenus.internal.codec.udt.IdenticalUDTCodec
import net.nmoncho.helenus.internal.codec.udt.NonIdenticalUDTCodec
import net.nmoncho.helenus.internal.codec.udt.UnifiedUDTCodec
import shapeless.Annotation
import shapeless.IsTuple
import shapeless.Witness

trait CodecDerivation extends TupleCodecDerivation { that =>

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

  // DSE TypeCodecs
  implicit final val lineStringCodec: TypeCodec[LineString] = TypeCodecs.lineStringCodec

  implicit final val pointCodec: TypeCodec[Point] = TypeCodecs.pointCodec

  implicit final val polygonCodec: TypeCodec[Polygon] = TypeCodecs.polygonCodec

  implicit final val dateRangeCodec: TypeCodec[DateRange] = TypeCodecs.dateRangeCodec

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

  implicit def bufferOf[T: TypeCodec]: TypeCodec[mutable.Buffer[T]] =
    TypeCodecs.mutableBufferOf(implicitly[TypeCodec[T]])

  implicit def mutableIndexedSeq[T: TypeCodec]: TypeCodec[mutable.IndexedSeq[T]] =
    TypeCodecs.mutableIndexedSeqOf(implicitly[TypeCodec[T]])

  implicit def mutableSet[T: TypeCodec]: TypeCodec[mutable.Set[T]] =
    TypeCodecs.mutableSetOf(implicitly[TypeCodec[T]])

  implicit def mutableMapOf[K: TypeCodec, V: TypeCodec]: TypeCodec[mutable.Map[K, V]] =
    TypeCodecs.mutableMapOf(implicitly[TypeCodec[K]], implicitly[TypeCodec[V]])

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

  /** Derives a [[ColumnMapper]] for a given type [[A]], given an available implicit [[TypeCodec]]
    */
  implicit def columnMapper[A: TypeCodec]: ColumnMapper[A] =
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

    /** Summons a [[Codec]] for type [[T]] */
    def of[T](
        implicit @implicitNotFound(
          "If ${T} is a simple type, then it isn't defined in `net.nmoncho.helenus`. " +
            "If ${T} is an Enumeration, it must tagged with some enum annotation. " +
            "Also make sure you aren't shadowing any implicit definition"
        )
        codec: TypeCodec[T]
    ): TypeCodec[T] =
      codec

    def of[T <: Product: ClassTag: IdenticalUDTCodec: NonIdenticalUDTCodec](
        keyspace: String = "",
        name: String     = "",
        frozen: Boolean  = true
    )(
        implicit columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = new UnifiedUDTCodec[T](
      IdenticalUDTCodec[T](keyspace, name, frozen),
      udt => NonIdenticalUDTCodec(udt)
    )

    /** Creates a [[TypeCodec]] for a case class
      *
      * The case class fields need to be in the same order as CQL type. If they aren't,
      * please use [[of]] for a codec that does a check a runtime, or if you already know fields won't be in the same
      * order [[nonIdenticalUdtCodecOf]] , or [[udtFromFields]].
      *
      * With this [[TypeCodec]] implementation case class field <em>name</em> don't have to be the same as the CQL type,
      * only <em>order</em> is relevant. Which may not be what you need.
      *
      * @param keyspace  in which keyspace is the CQL type registered in. Optional, only define this parameter if you are
      *                  going to register this codec, and the CQL type is on a different keyspace than the session.
      * @param name      CQL Type Name. Optional, defaults to the name of the case class with the column mapper applied.
      * @param frozen    where this type should be frozen or not.
      * @param columnMapper how to map the case class fields to the CQL Type, and it's name if not specified
      * @tparam T type of the case class
      * @return [[TypeCodec]] for the desired case class
      */
    @deprecated(
      message = "Use Codec.identicalUdtOf, this method will be removed in 2.0",
      since   = "1.7.0"
    )
    def udtOf[T: ClassTag: IdenticalUDTCodec](
        keyspace: String = "",
        name: String     = "",
        frozen: Boolean  = true
    )(
        implicit columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = identicalUdtOf[T](keyspace, name, frozen)

    /** Creates a [[TypeCodec]] for a case class
      *
      * The case class fields need to be in the same order as CQL type. If they aren't,
      * please use [[of]] for a codec that does a check a runtime, or if you already know fields won't be in the same
      * order [[nonIdenticalUdtCodecOf]] , or [[udtFromFields]].
      *
      * With this [[TypeCodec]] implementation case class field <em>name</em> don't have to be the same as the CQL type,
      * only <em>order</em> is relevant. Which may not be what you need.
      *
      * @param keyspace  in which keyspace is the CQL type registered in. Optional, only define this parameter if you are
      *                  going to register this codec, and the CQL type is on a different keyspace than the session.
      * @param name      CQL Type Name. Optional, defaults to the name of the case class with the column mapper applied.
      * @param frozen    where this type should be frozen or not.
      * @param columnMapper how to map the case class fields to the CQL Type, and it's name if not specified
      * @tparam T type of the case class
      * @return [[TypeCodec]] for the desired case class
      */
    def identicalUdtOf[T: ClassTag: IdenticalUDTCodec](
        keyspace: String = "",
        name: String     = "",
        frozen: Boolean  = true
    )(
        implicit columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = IdenticalUDTCodec[T](keyspace, name, frozen)

    /** Creates a [[TypeCodec]] for a case class
      *
      * Use this method when case class fields are <b>not</b> defined in the same order as CQL type. And
      * you want to align these two with metadata coming from the database. Mapping between the case class and the CQL type
      * happens by matching field names, use [[columnMapper]] to align these names.
      *
      * This method <b>requires</b> a connection to the database. If the context doesn't have a connection
      * and case class fields are not aligned with its CQL type, consider using [[udtFromFields]]
      *
      * @param session       used to get the session metadata
      * @param keyspace      in which keyspace is the CQL type registered in. Optional, defaults to session's keyspace.
      * @param name          CQL Type Name. Optional, defaults to the name of the case class with the column mapper applied.
      * @param columnMapper how to map the case class fields to the CQL Type, and it's name if not specified
      * @tparam T type of the case class
      * @return [[TypeCodec]] for the desired case class
      */
    @deprecated(
      message = "Use Codec.nonIdenticalUdtOf, this method will be removed in 2.0",
      since   = "1.7.0"
    )
    def udtFrom[T: ClassTag: NonIdenticalUDTCodec](
        session: CqlSession,
        keyspace: String = "",
        name: String     = ""
    )(
        implicit columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = NonIdenticalUDTCodec[T](session, keyspace, name)

    /** Creates a [[TypeCodec]] for a case class
      *
      * Use this method when case class fields are <b>not</b> defined in the same order as CQL type. And
      * you want to align these two with metadata coming from the database. Mapping between the case class and the CQL type
      * happens by matching field names, use [[columnMapper]] to align these names.
      *
      * This method <b>requires</b> a connection to the database. If the context doesn't have a connection
      * and case class fields are not aligned with its CQL type, consider using [[udtFromFields]]
      *
      * @param session       used to get the session metadata
      * @param keyspace      in which keyspace is the CQL type registered in. Optional, defaults to session's keyspace.
      * @param name          CQL Type Name. Optional, defaults to the name of the case class with the column mapper applied.
      * @param columnMapper how to map the case class fields to the CQL Type, and it's name if not specified
      * @tparam T type of the case class
      * @return [[TypeCodec]] for the desired case class
      */
    def nonIdenticalUdtCodecOf[T: ClassTag: NonIdenticalUDTCodec](
        session: CqlSession,
        keyspace: String = "",
        name: String     = ""
    )(
        implicit columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
    ): TypeCodec[T] = NonIdenticalUDTCodec[T](session, keyspace, name)

    /** Creates a [[TypeCodec]] for a case class
      *
      * Use this method when case class fields are <b>not</b> defined in the same order as CQL type. The `fields`
      * parameter let's you define in which order the CQL type fields are defined. Mapping between the case class and the CQL type
      * happens by matching field names, use [[columnMapper]] to align these names.
      *
      * Unlike other UDT Codec methods this doesn't support default arguments, as this is backed by a Scala Macro with
      * doesn't support them.
      *
      * @param keyspace in which keyspace is the CQL type registered in. Optional, empty string can be used.
      * @param name CQL Type Name. Optional, empty string can be used
      * @param frozen where this type should be frozen or not.
      * @param fields in which order the CQL type fields are defined
      * @param columnMapper how to map the case class fields to the CQL Type, and it's name if not specified
      * @tparam T type of the case class
      * @return [[TypeCodec]] for the desired case class
      */
    def udtFromFields[T](
        keyspace: String,
        name: String,
        frozen: Boolean
    )(fields: T => Any*)(
        implicit columnMapper: ColumnNamingScheme,
        classTag: ClassTag[T]
    ): TypeCodec[T] =
      macro net.nmoncho.helenus.internal.macros.NonIdenticalCodec.buildCodec[T]

    def tupleOf[T: IsTuple](implicit tupleCodec: TupleCodec[T]): TypeCodec[T] =
      that.tupleOf[T]

    /** Summons a [[Codec]] for type [[T]] */
    def apply[T](
        implicit @implicitNotFound(
          "If ${T} is a simple type, then it isn't defined in `net.nmoncho.helenus`. " +
            "If ${T} is an Enumeration, it must tagged with some enum annotation. " +
            "Also make sure you aren't shadowing any implicit definition"
        )
        codec: TypeCodec[T]
    ): TypeCodec[T] = codec
  }

}
