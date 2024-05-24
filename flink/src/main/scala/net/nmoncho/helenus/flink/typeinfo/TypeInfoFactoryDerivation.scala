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

import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util

import scala.annotation.implicitNotFound
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

import net.nmoncho.helenus.flink.typeinfo.TypeInfoFactory.DerivedTypeInfoFactory
import net.nmoncho.helenus.flink.typeinfo.TypeInfoFactory.mapped
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.common.typeinfo.{ TypeInfoFactory => FlinkTypeInfoFactory }
import org.apache.flink.api.common.typeinfo.{ TypeInformation => FlinkTypeInformation }
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.tuple
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.LabelledGeneric
import shapeless.Lazy
import shapeless.Witness
import shapeless.labelled.FieldType

class TypeInformation[T](val underlying: FlinkTypeInformation[T]) extends FlinkTypeInformation[T] {
  override def isBasicType: Boolean = underlying.isBasicType

  override def isTupleType: Boolean = underlying.isTupleType

  override def getArity: Int = underlying.getArity

  override def getTotalFields: Int = underlying.getTotalFields

  override def getTypeClass: Class[T] = underlying.getTypeClass

  override def isKeyType: Boolean = underlying.isKeyType

  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    underlying.createSerializer(config)

  override def canEqual(obj: Any): Boolean = underlying.canEqual(obj)

  override def hashCode(): Int = underlying.hashCode()

  override def equals(obj: Any): Boolean = underlying.equals(obj)

  override def toString: String = underlying.toString
}

object TypeInformation {

  def wrap[T](value: FlinkTypeInformation[T]): TypeInformation[T] = new TypeInformation[T](value)

  implicit def convert[T](implicit value: FlinkTypeInformation[T]): TypeInformation[T] =
    new TypeInformation[T](value)

  implicit val voidTypeInfo: TypeInformation[Void] = wrap(Types.VOID)

  implicit val unitTypeInfo: TypeInformation[Unit] = wrap(UnitTypeInformation)

  implicit val stringTypeInfo: TypeInformation[String] = wrap(Types.STRING)

  implicit val javaByteTypeInfo: TypeInformation[java.lang.Byte] = wrap(Types.BYTE)

  implicit val byteTypeInfo: TypeInformation[Byte] =
    javaByteTypeInfo.asInstanceOf[TypeInformation[Byte]]

  implicit val javaBooleanTypeInfo: TypeInformation[java.lang.Boolean] = wrap(Types.BOOLEAN)

  implicit val booleanTypeInfo: TypeInformation[Boolean] =
    javaBooleanTypeInfo.asInstanceOf[TypeInformation[Boolean]]

  implicit val javaShortTypeInfo: TypeInformation[java.lang.Short] = wrap(Types.SHORT)

  implicit val shortTypeInfo: TypeInformation[Short] =
    javaShortTypeInfo.asInstanceOf[TypeInformation[Short]]

  implicit val javaIntTypeInfo: TypeInformation[Integer] = wrap(Types.INT)

  implicit val intTypeInfo: TypeInformation[Int] =
    javaIntTypeInfo.asInstanceOf[TypeInformation[Int]]

  implicit val javaLongTypeInfo: TypeInformation[java.lang.Long] = wrap(Types.LONG)

  implicit val longTypeInfo: TypeInformation[Long] =
    javaLongTypeInfo.asInstanceOf[TypeInformation[Long]]

  implicit val javaFloatTypeInfo: TypeInformation[java.lang.Float] = wrap(Types.FLOAT)

  implicit val floatTypeInfo: TypeInformation[Float] =
    javaFloatTypeInfo.asInstanceOf[TypeInformation[Float]]

  implicit val javaDoubleTypeInfo: TypeInformation[java.lang.Double] = wrap(Types.DOUBLE)

  implicit val doubleTypeInfo: TypeInformation[Double] =
    javaDoubleTypeInfo.asInstanceOf[TypeInformation[Double]]

  implicit val javaCharTypeInfo: TypeInformation[Character] = wrap(Types.CHAR)

  implicit val charTypeInfo: TypeInformation[Char] =
    javaCharTypeInfo.asInstanceOf[TypeInformation[Char]]

  implicit def enumTypeInfo[T <: Enum[T]](implicit tag: ClassTag[T]): TypeInformation[T] =
    wrap(Types.ENUM(tag.runtimeClass.asInstanceOf[Class[T]]))

  implicit val instantTypeInfo: TypeInformation[Instant]             = wrap(Types.INSTANT)
  implicit val localDateTypeInfo: TypeInformation[LocalDate]         = wrap(Types.LOCAL_DATE)
  implicit val localTimeTypeInfo: TypeInformation[LocalTime]         = wrap(Types.LOCAL_TIME)
  implicit val localDateTimeTypeInfo: TypeInformation[LocalDateTime] = wrap(Types.LOCAL_DATE_TIME)

  implicit def flinkTupleTypeInfo[T <: tuple.Tuple](implicit tag: ClassTag[T]): TypeInformation[T] =
    wrap(Types.TUPLE(tag.runtimeClass.asInstanceOf[Class[T]]))

  implicit def primitiveArrayTypeInfo[T <: AnyVal](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    // Flink checks the inner type info, since we're wrapping it,
    // we need to get the underlying value for their check to work
    wrap(Types.PRIMITIVE_ARRAY(inner.underlying).asInstanceOf[FlinkTypeInformation[Array[T]]])

  implicit def objectArrayTypeInfo[T <: AnyRef](
      implicit inner: TypeInformation[T]
  ): TypeInformation[Array[T]] =
    wrap(Types.OBJECT_ARRAY[T](inner))

  implicit def javaMapTypeInfo[K, V](
      implicit key: TypeInformation[K],
      value: TypeInformation[V]
  ): TypeInformation[java.util.Map[K, V]] =
    wrap(Types.MAP(key, value))

  implicit def mapTypeInfo[K, V](
      implicit inner: TypeInformation[java.util.Map[K, V]]
  ): TypeInformation[Map[K, V]] =
    wrap(
      mapped[Map[K, V], java.util.Map[K, V]](
        _.asScala.toMap,
        _.asJava
      )
    )

  implicit def javaListTypeInfo[T](
      implicit inner: TypeInformation[T]
  ): TypeInformation[java.util.List[T]] =
    wrap(Types.LIST(inner))

  implicit def listTypeInfo[T](
      implicit inner: TypeInformation[java.util.List[T]]
  ): TypeInformation[List[T]] =
    wrap(
      mapped[List[T], java.util.List[T]](
        _.asScala.toList,
        _.asJava
      )
    )
}

object TypeInfoFactory {

  def mapped[Outer: ClassTag, Inner: TypeInformation](
      toOuter: Inner => Outer,
      toInner: Outer => Inner
  ): FlinkTypeInformation[Outer] =
    MappedTypedInformation[Outer, Inner](toOuter, toInner)

  trait DerivedTypeInfoFactory[T] {
    def fields: Map[String, FlinkTypeInformation[_]]
  }

  implicit def hnilTypeInfoFactory: DerivedTypeInfoFactory[HNil] =
    new DerivedTypeInfoFactory[HNil] {
      override val fields: Map[String, FlinkTypeInformation[_]] = Map.empty
    }

  implicit def hlistTypeInfoFactory[K <: Symbol, H, T <: HList](
      implicit @implicitNotFound("Couldn't find type info [${H}]") typeInfo: TypeInformation[H],
      witness: Witness.Aux[K],
      tailFactory: Lazy[DerivedTypeInfoFactory[T]]
  ): DerivedTypeInfoFactory[FieldType[K, H] :: T] =
    new DerivedTypeInfoFactory[FieldType[K, H] :: T] {
      override def fields: Map[String, FlinkTypeInformation[_]] =
        tailFactory.value.fields + (witness.value.name -> typeInfo.underlying)
    }

  implicit def genericTypeInfoFactory[A, R](
      implicit gen: LabelledGeneric.Aux[A, R],
      factory: Lazy[DerivedTypeInfoFactory[R]]
  ): DerivedTypeInfoFactory[A] =
    new DerivedTypeInfoFactory[A] {
      override def fields: Map[String, FlinkTypeInformation[_]] =
        factory.value.fields
    }

  def pojoFactory[T](
      implicit factory: DerivedTypeInfoFactory[T],
      tag: ClassTag[T]
  ): FlinkTypeInformation[T] = {
    import scala.jdk.CollectionConverters._

    Types.POJO(
      tag.runtimeClass.asInstanceOf[Class[T]],
      factory.fields.asJava
    )
  }

}

abstract class PojoTypeInfoFactory[T: DerivedTypeInfoFactory: ClassTag]
    extends FlinkTypeInfoFactory[T] {

  override def createTypeInfo(
      t: Type,
      genericParameters: util.Map[String, FlinkTypeInformation[_]]
  ): FlinkTypeInformation[T] = TypeInfoFactory.pojoFactory[T]

}
