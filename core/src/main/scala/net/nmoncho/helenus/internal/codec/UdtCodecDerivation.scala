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

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.{ MappingCodec, TypeCodec }
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{ DataType, UserDefinedType }
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.api.core.{ CqlIdentifier, CqlSession }
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import com.datastax.oss.driver.internal.core.`type`.codec.{ UdtCodec => DseUdtCodec }
import net.nmoncho.helenus.api.`type`.codec.{ ColumnMapper, DefaultColumnMapper, Udt }
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps

import scala.reflect.ClassTag

/** UDT codec derivation represented as Case Classes.
  */
trait UdtCodecDerivation {

  import shapeless._

  trait UdtCodec[T] {
    private[UdtCodecDerivation] def definitions: Seq[(String, DataType)]

    def innerToOuter(value: UdtValue): T

    def outerToInner(udt: UdtValue, value: T): UdtValue
  }

  /** Derives a [[TypeCodec]] for type [[T]], by looking to the [[UserDefinedType]] in the [[CqlSession]].
    *
    * This assumes that both the case class' fields and the CQL Type's fields have the same order. If this is not the
    * case, consider using [[udtFrom(session)]].
    *
    * @param annotation used to get the session metadata
    * @param columnMapper how to map case class's field names ot CQL Type's field names
    * @tparam T case class type to derive [[TypeCodec]] for
    */
  implicit def udtOf[T <: Product with Serializable: ClassTag: UdtCodec](
      implicit annotation: Annotation[Udt, T],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): TypeCodec[T] = mappingCodec[T](generateUserDefinedType[T])

  /** Derives a [[TypeCodec]] for type [[T]], by looking to the [[UserDefinedType]] in the [[CqlSession]].
    *
    * Use this method when the order of the case class' fields is different from the CQL Type's fields.
    *
    * @param session used to get the session metadata
    * @param annotation UDT metadata information
    * @param columnMapper how to map case class's field names ot CQL Type's field names
    * @tparam T case class type to derive [[TypeCodec]] for
    */
  def udtFrom[T <: Product with Serializable: ClassTag: UdtCodec](session: CqlSession)(
      implicit annotation: Annotation[Udt, T],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): TypeCodec[T] = {
    import scala.jdk.OptionConverters._
    val udtAnn = annotation()

    (for {
      keyspace <- session.getMetadata.getKeyspace(udtAnn.keyspace).toScala
      udt <- keyspace.getUserDefinedType(udtAnn.name).toScala
    } yield mappingCodec[T](udt)).getOrElse(
      throw new IllegalArgumentException(
        s"Cannot create TypeCodec for ${implicitly[ClassTag[T]].runtimeClass}. Couldn't find type [${udtAnn.name}] in keyspace [${udtAnn.keyspace}]"
      )
    )
  }

  private def mappingCodec[T <: Product with Serializable](udt: UserDefinedType)(
      implicit udtCodec: UdtCodec[T],
      annotation: Annotation[Udt, T],
      classTag: ClassTag[T],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): TypeCodec[T] = new MappingCodec[UdtValue, T](
    new DseUdtCodec(udt),
    GenericType.of(
      classTag.runtimeClass.asInstanceOf[Class[T]]
    )
  ) {
    override val getCqlType: UserDefinedType =
      super.getCqlType.asInstanceOf[UserDefinedType]

    override def innerToOuter(value: UdtValue): T =
      udtCodec.innerToOuter(value)

    override def outerToInner(value: T): UdtValue =
      udtCodec.outerToInner(getCqlType.newValue(), value)

    override lazy val toString: String =
      s"UtdCodec[${implicitly[ClassTag[T]].runtimeClass.getSimpleName}]"
  }

  /** Generate a [[UserDefinedType]] from an [[Udt]] annotation.
    *
    * This assumes the UDT has the same type order definition as the case class. Name fields aren't important during
    * decode/encode, only <b>order</b> is important
    */
  private def generateUserDefinedType[T](
      implicit udtCodec: UdtCodec[T],
      annotation: Annotation[Udt, T]
  ): UserDefinedType = {
    import scala.jdk.CollectionConverters._

    val udtAnn = annotation()

    val (identifiers, dataTypes) =
      udtCodec.definitions.foldRight(List.empty[CqlIdentifier] -> List.empty[DataType]) {
        case ((name, dataType), (identifiers, dataTypes)) =>
          (CqlIdentifier.fromInternal(name) :: identifiers) -> (dataType :: dataTypes)
      }

    new DefaultUserDefinedType(
      CqlIdentifier.fromInternal(udtAnn.keyspace),
      CqlIdentifier.fromInternal(udtAnn.name),
      false,
      identifiers.asJava,
      dataTypes.asJava
    )
  }

  implicit def lastUdtElementCodec[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): UdtCodec[FieldType[K, H] :: HNil] =
    new UdtCodec[FieldType[K, H] :: HNil] {
      private val column = columnMapper.map(witness.value.name)

      private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] = Seq(
        column -> codec.getCqlType
      )

      override def innerToOuter(value: UdtValue): FieldType[K, H] :: HNil =
        (witness.value ->> value.get(column, codec)).asInstanceOf[FieldType[K, H]] :: HNil

      override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: HNil): UdtValue =
        udt.set(column, value.head, codec)
    }

  implicit def hListUdtCodec[K <: Symbol, H, T <: HList](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      udtCodec1: UdtCodec[T],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): UdtCodec[FieldType[K, H] :: T] =
    new UdtCodec[FieldType[K, H] :: T] {

      private val column = columnMapper.map(witness.value.name)

      private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] =
        Seq(column -> codec.getCqlType) ++ udtCodec1.definitions

      override def innerToOuter(value: UdtValue): FieldType[K, H] :: T =
        (witness.value ->> value.get(column, codec))
          .asInstanceOf[FieldType[K, H]] :: udtCodec1.innerToOuter(value)

      override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: T): UdtValue =
        udtCodec1.outerToInner(
          udt.set(column, value.head, codec),
          value.tail
        )
    }

  implicit def genericUdtCodec[A <: Product with Serializable, R](
      implicit generic: LabelledGeneric.Aux[A, R],
      codec: UdtCodec[R],
      columnMapper: ColumnMapper = DefaultColumnMapper
  ): UdtCodec[A] = new UdtCodec[A] {

    private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] = codec.definitions

    override def innerToOuter(value: UdtValue): A =
      generic.from(codec.innerToOuter(value))

    override def outerToInner(udt: UdtValue, value: A): UdtValue =
      codec.outerToInner(udt, generic.to(value))
  }
}
