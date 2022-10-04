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

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.`type`.codec.{ MappingCodec, TypeCodec }
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.{ DataType, UserDefinedType }
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import com.datastax.oss.driver.internal.core.`type`.codec.{ UdtCodec => DseUdtCodec }
import net.nmoncho.helenus.api.`type`.codec.Udt
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

  implicit def udtOf[T <: Product with Serializable: ClassTag](
      implicit udtCodec: UdtCodec[T],
      annotation: Annotation[Udt, T]
  ): TypeCodec[T] = {
    import scala.jdk.CollectionConverters._

    val udtAnn = annotation()

    val (identifiers, dataTypes) =
      udtCodec.definitions.foldRight(List.empty[CqlIdentifier] -> List.empty[DataType]) {
        case ((name, dataType), (identifiers, dataTypes)) =>
          (CqlIdentifier.fromInternal(name) :: identifiers) -> (dataType :: dataTypes)
      }

    val udt = new DefaultUserDefinedType(
      CqlIdentifier.fromInternal(udtAnn.keyspace),
      CqlIdentifier.fromInternal(udtAnn.name),
      false,
      identifiers.asJava,
      dataTypes.asJava
    )

    new MappingCodec[UdtValue, T](
      new DseUdtCodec(udt),
      GenericType.of(
        implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
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
  }

  implicit def lastUdtElementCodec[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K]
  ): UdtCodec[FieldType[K, H] :: HNil] =
    new UdtCodec[FieldType[K, H] :: HNil] {
      println(codec.getCqlType)

      private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] = Seq(
        witness.value.name -> codec.getCqlType
      )

      override def innerToOuter(value: UdtValue): FieldType[K, H] :: HNil = {
        val name = witness.value.name

        (witness.value ->> value.get(name, codec)).asInstanceOf[FieldType[K, H]] :: HNil
      }

      override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: HNil): UdtValue = {
        val name = witness.value.name
        udt.set(name, value.head, codec)
      }
    }

  implicit def hListUdtCodec[K <: Symbol, H, T <: HList](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      udtCodec1: UdtCodec[T]
  ): UdtCodec[FieldType[K, H] :: T] =
    new UdtCodec[FieldType[K, H] :: T] {

      println(codec.getCqlType)

      private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] =
        Seq(witness.value.name -> codec.getCqlType) ++ udtCodec1.definitions

      override def innerToOuter(value: UdtValue): FieldType[K, H] :: T = {
        val name = witness.value.name
        (witness.value ->> value.get(name, codec))
          .asInstanceOf[FieldType[K, H]] :: udtCodec1.innerToOuter(value)
      }

      override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: T): UdtValue = {
        val name = witness.value.name
        udtCodec1.outerToInner(
          udt.set(name, value.head, codec),
          value.tail
        )
      }
    }

  implicit def genericUdtCodec[A <: Product with Serializable, R](
      implicit generic: LabelledGeneric.Aux[A, R],
      codec: UdtCodec[R]
  ): UdtCodec[A] = new UdtCodec[A] {

    private[UdtCodecDerivation] val definitions: Seq[(String, DataType)] = codec.definitions

    override def innerToOuter(value: UdtValue): A =
      generic.from(codec.innerToOuter(value))

    override def outerToInner(udt: UdtValue, value: A): UdtValue =
      codec.outerToInner(udt, generic.to(value))
  }
}
