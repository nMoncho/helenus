/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import scala.reflect.ClassTag

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils._
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.core.memory.DataOutputView

// $COVERAGE-OFF$
/** Provides a [[WrappedTypeInformation]] implementation for a [[Outer]] by mapping
  * another implementation for [[Inner]]
  *
  * @param toOuter how to convert from inner to outer
  * @param toInner how to convert from outer to inner
  * @tparam Outer the type we desired an implementation for
  * @tparam Inner the type that already as an implementation
  */
class MappedTypedInformation[Outer, Inner](
    toOuter: Inner => Outer,
    toInner: Outer => Inner,
    tag: ClassTag[Outer],
    inner: TypeInformation[Inner]
) extends TypeInformation[Outer] {

  override val isBasicType: Boolean = inner.isBasicType

  override val isTupleType: Boolean = inner.isTupleType

  override val getArity: Int = inner.getArity

  override val getTotalFields: Int = inner.getTotalFields

  override val getTypeClass: Class[Outer] =
    tag.runtimeClass.asInstanceOf[Class[Outer]]

  override val isKeyType: Boolean = inner.isKeyType

  override def createSerializer(config: ExecutionConfig): TypeSerializer[Outer] =
    createSer(inner.createSerializer(config))

  override def canEqual(obj: Any): Boolean = inner.canEqual(obj)

  private def createSer(serializer: TypeSerializer[Inner]): TypeSerializer[Outer] =
    new MappedSerializer(serializer)

  private class MappedSerializer(val serializer: TypeSerializer[Inner])
      extends TypeSerializer[Outer] {
    override def isImmutableType: Boolean =
      serializer.isImmutableType

    override def duplicate(): TypeSerializer[Outer] =
      createSer(serializer.duplicate())

    override def createInstance(): Outer =
      toOuter(serializer.createInstance())

    override def copy(from: Outer): Outer =
      toOuter(serializer.copy(toInner(from)))

    override def copy(from: Outer, reuse: Outer): Outer =
      toOuter(serializer.copy(toInner(from), toInner(reuse)))

    override def getLength: Int = serializer.getLength

    override def serialize(record: Outer, target: DataOutputView): Unit =
      serializer.serialize(toInner(record), target)

    override def deserialize(source: DataInputView): Outer =
      toOuter(serializer.deserialize(source))

    override def deserialize(reuse: Outer, source: DataInputView): Outer =
      toOuter(serializer.deserialize(toInner(reuse), source))

    override def copy(source: DataInputView, target: DataOutputView): Unit =
      serializer.copy(source, target)

    override def snapshotConfiguration(): TypeSerializerSnapshot[Outer] = {
      val snapshot = serializer.snapshotConfiguration()

      new TypeSerializerSnapshot[Outer] {
        override def getCurrentVersion: Int =
          snapshot.getCurrentVersion

        override def writeSnapshot(out: DataOutputView): Unit =
          snapshot.writeSnapshot(out)

        override def readSnapshot(
            readVersion: Int,
            in: DataInputView,
            userCodeClassLoader: ClassLoader
        ): Unit =
          snapshot.readSnapshot(readVersion, in, userCodeClassLoader)

        override def restoreSerializer(): TypeSerializer[Outer] =
          createSer(snapshot.restoreSerializer())

        override def resolveSchemaCompatibility(
            newSerializer: TypeSerializer[Outer]
        ): TypeSerializerSchemaCompatibility[Outer] =
          newSerializer match {
            case mapped: MappedSerializer =>
              val comp = snapshot.resolveSchemaCompatibility(mapped.serializer)
              if (comp.isCompatibleAsIs) {
                TypeSerializerSchemaCompatibility.compatibleAsIs()
              } else if (comp.isIncompatible) {
                TypeSerializerSchemaCompatibility.incompatible()
              } else if (comp.isCompatibleAfterMigration) {
                TypeSerializerSchemaCompatibility.compatibleAfterMigration()
              } else {
                TypeSerializerSchemaCompatibility.compatibleWithReconfiguredSerializer(
                  createSer(mapped.serializer)
                )
              }

            case _ =>
              TypeSerializerSchemaCompatibility.incompatible()
          }

      }
    }

    override def hashCode(): Int = 0

    override def equals(obj: Any): Boolean = false

    override def toString: String = ""
  }

  override def hashCode(): Int = 0

  override def equals(obj: Any): Boolean = false

  override def toString: String = ""
}

object MappedTypedInformation {
  def apply[Outer, Inner](toOuter: Inner => Outer, toInner: Outer => Inner)(
      implicit tag: ClassTag[Outer],
      inner: TypeInformation[Inner]
  ): MappedTypedInformation[Outer, Inner] =
    new MappedTypedInformation(toOuter, toInner, tag, inner)
}
// $COVERAGE-ON$
