/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import scala.annotation.implicitNotFound
import scala.reflect.ClassTag

import org.apache.flink.api.common.typeinfo.TypeInformation
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.LabelledGeneric
import shapeless.Lazy
import shapeless.Witness
import shapeless.labelled.FieldType

trait TypeInformationDerivation {

  implicit def convert[T](
      implicit value: TypeInformation[T]
  ): TypeInformationDerivation.WrappedTypeInformation[T] =
    new TypeInformationDerivation.WrappedTypeInformation[T](value)

  // Derivation
  implicit def hnilTypeInfoFactory: TypeInformationDerivation.DerivedTypeInfoFactory[HNil] =
    new TypeInformationDerivation.DerivedTypeInfoFactory[HNil] {
      override val fields: Map[String, TypeInformation[_]] = Map.empty
    }

  implicit def hlistTypeInfoFactory[K <: Symbol, H, T <: HList](
      implicit @implicitNotFound(
        "Couldn't find type info [${H}]"
      ) typeInfo: TypeInformationDerivation.WrappedTypeInformation[H],
      witness: Witness.Aux[K],
      tailFactory: Lazy[TypeInformationDerivation.DerivedTypeInfoFactory[T]]
  ): TypeInformationDerivation.DerivedTypeInfoFactory[FieldType[K, H] :: T] =
    new TypeInformationDerivation.DerivedTypeInfoFactory[FieldType[K, H] :: T] {
      override def fields: Map[String, TypeInformation[_]] =
        tailFactory.value.fields + (witness.value.name -> typeInfo.underlying)
    }

  implicit def genericTypeInfoFactory[A, R](
      implicit gen: LabelledGeneric.Aux[A, R],
      factory: Lazy[TypeInformationDerivation.DerivedTypeInfoFactory[R]]
  ): TypeInformationDerivation.DerivedTypeInfoFactory[A] =
    new TypeInformationDerivation.DerivedTypeInfoFactory[A] {
      override def fields: Map[String, TypeInformation[_]] =
        factory.value.fields
    }

  def Pojo[T](
      implicit factory: TypeInformationDerivation.DerivedTypeInfoFactory[T],
      tag: ClassTag[T]
  ): TypeInformation[T] = {
    import scala.jdk.CollectionConverters._

    org.apache.flink.api.common.typeinfo.Types.POJO(
      tag.runtimeClass.asInstanceOf[Class[T]],
      factory.fields.asJava
    )
  }
}

object TypeInformationDerivation extends TypeInformationDerivation with ImplicitTypes {

  trait DerivedTypeInfoFactory[T] {
    def fields: Map[String, TypeInformation[_]]
  }

  class WrappedTypeInformation[T](val underlying: TypeInformation[T])

}
