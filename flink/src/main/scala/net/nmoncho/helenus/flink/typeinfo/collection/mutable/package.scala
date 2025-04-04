/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo.collection

import scala.collection.compat._
import scala.collection.{ mutable => mutablecoll }

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerSnapshot
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot

import IterableTypeInformation.Serializer

// $COVERAGE-OFF$
package object mutable {

  abstract class MutableIterableTypeInformation[T, Iter[T] <: scala.collection.mutable.Iterable[T]](
      inner: TypeInformation[T]
  )(
      implicit factory: Factory[T, Iter[T]]
  ) extends IterableTypeInformation[T, Iter](inner) {

    override def createSerializer(config: ExecutionConfig): TypeSerializer[Iter[T]] = {
      val innerSerializer = inner.createSerializer(config)

      new MutableSerializer[T, Iter](innerSerializer, factory)
    }
  }

  class MutableSerializer[T, Iter[T] <: scala.collection.mutable.Iterable[T]](
      innerSerializer: TypeSerializer[T],
      factory: Factory[T, Iter[T]]
  ) extends Serializer[T, Iter](innerSerializer, factory) {
    override def isImmutableType: Boolean = false

    override def copy(from: Iter[T]): Iter[T] =
      from
        .foldLeft(factory.newBuilder) { (iter, value) =>
          iter += value
          iter
        }
        .result()

    override def snapshotConfiguration(): TypeSerializerSnapshot[Iter[T]] =
      new CompositeTypeSerializerSnapshot[Iter[T], Serializer[T, Iter]](this.getClass) {

        override def getCurrentOuterSnapshotVersion: Int = 1

        override def getNestedSerializers(
            outerSerializer: Serializer[T, Iter]
        ): Array[TypeSerializer[_]] =
          Array(outerSerializer.innerSerializer)

        override def createOuterSerializerWithNestedSerializers(
            nestedSerializers: Array[TypeSerializer[_]]
        ): Serializer[T, Iter] =
          new MutableSerializer[T, Iter](
            nestedSerializers(0).asInstanceOf[TypeSerializer[T]],
            factory
          )

      }
  }

  class BufferTypeInformation[T](inner: TypeInformation[T])
      extends MutableIterableTypeInformation[T, mutablecoll.Buffer](inner) {

    override def toString(): String = s"Buffer[${inner.toString}]"
  }

  class IndexedSeqTypeInformation[T](inner: TypeInformation[T])
      extends MutableIterableTypeInformation[T, mutablecoll.IndexedSeq](inner) {

    override def toString(): String = s"IndexedSeq[${inner.toString}]"
  }

  class SetTypeInformation[T](inner: TypeInformation[T])
      extends MutableIterableTypeInformation[T, mutablecoll.Set](inner) {

    override def toString(): String = s"Set[${inner.toString}]"
  }

}
// $COVERAGE-ON$
