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

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.{ TypeInformation => FlinkTypeInformation }
import org.apache.flink.api.common.typeutils._
import org.apache.flink.api.common.typeutils.base.TypeSerializerSingleton
import org.apache.flink.core.memory.DataInputView
import org.apache.flink.core.memory.DataOutputView

/** [[TypeInformation]] implementation for [[Unit]]
  * Follows a similar implementation than for [[Void]]
  */
object UnitTypeInformation extends FlinkTypeInformation[Unit] {

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
