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

package net.nmoncho.helenus.flink.typeinfo.collection

import scala.collection.compat._

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.core.memory.DataOutputView

// $COVERAGE-OFF$
abstract class IterableTypeInformation[T, Iter[T] <: scala.collection.Iterable[T]](
    val inner: TypeInformation[T]
)(
    implicit factory: Factory[T, Iter[T]]
) extends TypeInformation[Iter[T]] {

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 0

  // similar as arrays, the lists are "opaque" to the direct field addressing logic
  // since the list's elements are not addressable, we do not expose them
  override def getTotalFields: Int = 1

  override def getTypeClass: Class[Iter[T]] = this.getClass.asInstanceOf[Class[Iter[T]]]

  override def isKeyType: Boolean = false

  override def hashCode(): Int = 31 * inner.hashCode + 1

  override def canEqual(obj: Any): Boolean = obj != null && obj.getClass == obj.getClass

  override def equals(obj: Any): Boolean =
    if (obj == this) {
      true
    } else {
      obj match {
        case other: IterableTypeInformation[_, _] =>
          other.getClass == this.getClass && other.inner == this.inner

        case _ =>
          false
      }
    }

  override def createSerializer(config: ExecutionConfig): TypeSerializer[Iter[T]] = {
    val innerSerializer = inner.createSerializer(config)

    new IterableTypeInformation.Serializer[T, Iter](innerSerializer, factory)
  }
}

object IterableTypeInformation {

  class Serializer[T, Iter[T] <: scala.collection.Iterable[T]](
      val innerSerializer: TypeSerializer[T],
      factory: Factory[T, Iter[T]]
  ) extends TypeSerializer[Iter[T]] {

    override def isImmutableType: Boolean = true

    override def duplicate(): TypeSerializer[Iter[T]] = {
      val duplicateSerializer = innerSerializer.duplicate()

      if (duplicateSerializer eq innerSerializer) this
      else new Serializer(innerSerializer, factory)
    }

    override def createInstance(): Iter[T] =
      factory.newBuilder.result()

    override def copy(from: Iter[T]): Iter[T] =
      from // immutable, no need to copy

    override def copy(from: Iter[T], reuse: Iter[T]): Iter[T] =
      copy(from)

    override def getLength: Int = -1 // variable length

    override def serialize(record: Iter[T], target: DataOutputView): Unit = {
      target.writeInt(record.size)

      record.foreach(innerSerializer.serialize(_, target))
    }

    override def deserialize(source: DataInputView): Iter[T] = {
      val size = source.readInt()

      (0 until size)
        .foldLeft(factory.newBuilder) { (builder, _) =>
          builder.addOne(innerSerializer.deserialize(source))
        }
        .result()
    }

    override def deserialize(reuse: Iter[T], source: DataInputView): Iter[T] =
      deserialize(source)

    override def copy(source: DataInputView, target: DataOutputView): Unit = {
      val size = source.readInt

      target.writeInt(size)
      (0 until size).foreach(_ => innerSerializer.copy(source, target))
    }

    override def equals(obj: Any): Boolean = obj match {
      case other: Serializer[_, _] => other.innerSerializer == this.innerSerializer
      case _ => false
    }

    override def hashCode: Int = innerSerializer.hashCode

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
          new Serializer[T, Iter](
            nestedSerializers(0).asInstanceOf[TypeSerializer[T]],
            factory
          )

      }
  }

}
// $COVERAGE-ON$
