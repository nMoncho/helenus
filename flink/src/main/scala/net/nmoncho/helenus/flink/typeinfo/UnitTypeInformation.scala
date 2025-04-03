/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils._
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.core.memory.DataOutputView

// $COVERAGE-OFF$
/** [[WrappedTypeInformation]] implementation for [[Unit]]
  * Follows a similar implementation than for [[Void]]
  */
object UnitTypeInformation extends TypeInformation[Unit] {

  override def isBasicType: Boolean = true

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[Unit] = classOf[Unit]

  override def isKeyType: Boolean = true

  override def canEqual(obj: Any): Boolean = obj match {
    case _: UnitTypeInformation.type => true
    case _ => false
  }

  override def createSerializer(config: ExecutionConfig): TypeSerializer[Unit] =
    UnitTypeInformation.Serializer

  val Serializer: TypeSerializer[Unit] = new TypeSerializerSingleton[Unit] {
    override def isImmutableType: Boolean = true

    override def createInstance(): Unit = ()

    override def copy(from: Unit): Unit = from

    override def copy(from: Unit, reuse: Unit): Unit = ()

    override def getLength: Int = 1

    override def serialize(record: Unit, target: DataOutputView): Unit =
      // make progress in the stream, write one byte
      target.write(0)

    override def deserialize(source: DataInputView): Unit = {
      source.readByte()
      ()
    }

    override def deserialize(reuse: Unit, source: DataInputView): Unit = {
      source.readByte()
      ()
    }

    override def copy(source: DataInputView, target: DataOutputView): Unit =
      target.write(source.readByte())

    override def snapshotConfiguration(): TypeSerializerSnapshot[Unit] =
      new SimpleTypeSerializerSnapshot[Unit](() => UnitTypeInformation.Serializer) {}
  }

  override def hashCode(): Int = 0

  override def equals(obj: Any): Boolean = obj match {
    case _: UnitTypeInformation.type => true
    case _ => false
  }

  override def toString: String = "UnitTypeInformation"
}
// $COVERAGE-ON$
