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

package net.nmoncho.helenus
package internal.codec

import java.nio.ByteBuffer

import scala.collection.mutable
import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps

/** A [[IdenticalUDTCodec]] is one that maps a case class to a UDT, both having the <strong>same</strong>
  * field order, for example:
  *
  * {{{
  * case class IceCream(name: String, numCherries: Int, cone: Boolean)
  *
  * TYPE ice_cream(name TEXT, num_cherries INT, cone BOOLEAN)
  * }}}
  *
  * For case classes and UDTs that don't align on the field order, please use [[NonIdenticalUDTCodec]].
  * See our Developer Notes for more information.
  *
  * This trait follows [[TypeCodec]] interface closely, with some "tail-rec" modifications to handle
  * shapeless encoding.
  *
  * @tparam A case class being encoded
  */
trait IdenticalUDTCodec[A] {

  def columns: List[(String, DataType)]

  /** Encodes an [[A]] value as a [[ByteBuffer]], and appends it to a list
    *
    * @param value           value to be encoded
    * @param protocolVersion DSE Version in use
    * @return accumulated buffers, one per case class component, with the total buffer size
    */
  @inline def encode(value: A, protocolVersion: ProtocolVersion): (List[ByteBuffer], Int)

  /** Decodes an [[A]] value from a [[ByteCodec]]
    *
    * @param buffer buffer to be decoded
    * @param protocolVersion DSE Version in use
    * @return a decoded [[A]] value
    */
  @inline def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): A

  /** Formats an [[A]] value
    *
    * @param value value to be formatted
    * @param sb where to format into
    * @return builder with formatted value
    */
  @inline def format(value: A, sb: mutable.StringBuilder): mutable.StringBuilder

  /** Parses an [[A]] value from a String
    *
    * @param value where to parse from
    * @param idx from which index to start parsing
    * @return a parsed [[A]] value, and an index from where to continue parsing
    */
  @inline def parse(value: String, idx: Int): (A, Int)
}

object IdenticalUDTCodec {
  import shapeless._

  private val openingChar = '{'
  private val closingChar = '}'

  /** Creates [[TypeCodec]] for [[A]].
    *
    * Use this method when case class and UDT have the same field order. Otherwise use [[NonIdenticalUDTCodec]]
    *
    * @param keyspace           where is the CQL Type defined. If left empty, the session's keyspace will be used.
    * @param name               CQL Type name. If left empty, [[columnNamingScheme]] will be used to derive name for the case class name
    * @param codec              implicit codec
    * @param tag                implicit class tag
    * @param columnNamingScheme how to map field names between CQL Type and Case Class.
    * @tparam A case class type
    * @return [[TypeCodec]] for [[A]]
    */
  def apply[A](keyspace: String, name: String, frozen: Boolean)(
      implicit codec: IdenticalUDTCodec[A],
      tag: ClassTag[A],
      columnNamingScheme: ColumnNamingScheme = DefaultColumnNamingScheme
  ): TypeCodec[A] = new TypeCodec[A] {

    private val actualName =
      if (name.isBlank) columnNamingScheme.map(tag.runtimeClass.getSimpleName)
      else name

    override val getJavaType: GenericType[A] =
      GenericType.of(tag.runtimeClass.asInstanceOf[Class[A]])

    override val getCqlType: DataType = {
      import scala.jdk.CollectionConverters._

      val (identifiers, dataTypes) =
        codec.columns.foldRight(List.empty[CqlIdentifier] -> List.empty[DataType]) {
          case ((name, dataType), (identifiers, dataTypes)) =>
            (CqlIdentifier.fromInternal(name) :: identifiers) -> (dataType :: dataTypes)
        }

      new DefaultUserDefinedType(
        CqlIdentifier.fromInternal(keyspace),
        CqlIdentifier.fromInternal(actualName),
        frozen,
        identifiers.asJava,
        dataTypes.asJava
      )
    }

    override def encode(value: A, protocolVersion: ProtocolVersion): ByteBuffer =
      if (value == null) null
      else {
        val (buffers, size) = codec.encode(value, protocolVersion)
        val result          = ByteBuffer.allocate(size)

        buffers.foreach { field =>
          if (field == null) result.putInt(-1)
          else {
            result.putInt(field.remaining())
            result.put(field.duplicate())
          }
        }

        result.flip()
      }

    override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): A =
      codec.decode(buffer, protocolVersion)

    override def format(value: A): String =
      if (value == null) NULL
      else {
        val sb = new mutable.StringBuilder(openingChar)

        codec.format(value, sb)

        sb.append(closingChar).toString()
      }

    override def parse(value: String): A = if (
      value == null || value.isEmpty || value.equalsIgnoreCase(NULL)
    ) {
      null.asInstanceOf[A]
    } else {
      // we no longer care about the position here, just the parsed value
      val (parsed, _) = codec.parse(value, 0)

      parsed
    }

    override lazy val toString: String =
      s"UtdCodec[${tag.runtimeClass.getSimpleName}]"
  }

  /** Last UDT/CaseClass element
    */
  implicit def lastUdtCComponent[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      columnNamingScheme: ColumnNamingScheme = DefaultColumnNamingScheme
  ): IdenticalUDTCodec[FieldType[K, H] :: HNil] = new IdenticalUDTCodec[FieldType[K, H] :: HNil] {

    private val fieldName: String = witness.value.name
    private val column: String    = columnNamingScheme.map(fieldName)

    override val columns: List[(String, DataType)] = List(column -> codec.getCqlType)

    @inline override def encode(
        value: FieldType[K, H] :: HNil,
        protocolVersion: ProtocolVersion
    ): (List[ByteBuffer], Int) = {
      val encoded = codec.encode(value.head, protocolVersion)
      val size    = if (encoded == null) 4 else 4 + encoded.remaining()

      List(encoded) -> size
    }

    @inline override def decode(
        buffer: ByteBuffer,
        protocolVersion: ProtocolVersion
    ): FieldType[K, H] :: HNil =
      if (buffer == null) null.asInstanceOf[FieldType[K, H] :: HNil]
      else {
        val input = buffer.duplicate()

        val elementSize = input.getInt
        val element = if (elementSize < 0) {
          null.asInstanceOf[H]
        } else {
          val element = input.slice()
          element.limit(elementSize)
          input.position(input.position() + elementSize)

          codec.decode(element, protocolVersion)
        }

        (witness.value ->> element).asInstanceOf[FieldType[K, H]] :: HNil
      }

    @inline override def format(
        value: FieldType[K, H] :: HNil,
        sb: mutable.StringBuilder
    ): mutable.StringBuilder =
      sb.append(
        columnNamingScheme.asCql(fieldName, pretty = true)
      ).append(":")
        .append(
          if (value == null || value.head == null) NULL
          else codec.format(value.head)
        )

    @inline override def parse(value: String, idx: Int): (FieldType[K, H] :: HNil, Int) = {
      val start         = ParseUtils.skipSpaces(value, idx + 1)
      val (parsed, end) = parseWithCodec(value, codec, start)

      val next = ParseUtils.skipSpaces(value, end)
      if (next >= value.length) {
        throw new IllegalArgumentException(
          s"Malformed tuple value '$value', expected something else but got EOF"
        )
      }

      ((witness.value ->> parsed).asInstanceOf[FieldType[K, H]] :: HNil) -> next
    }
  }

  /** HList UDT/CaseClass element
    */
  implicit def hListUdtCComponent[K <: Symbol, H, T <: HList](
      implicit headCodec: TypeCodec[H],
      witness: Witness.Aux[K],
      tailCodec: IdenticalUDTCodec[T],
      columnNamingScheme: ColumnNamingScheme = DefaultColumnNamingScheme
  ): IdenticalUDTCodec[FieldType[K, H] :: T] =
    new IdenticalUDTCodec[FieldType[K, H] :: T] {

      private val fieldName: String = witness.value.name
      private val column: String    = columnNamingScheme.map(fieldName)

      override val columns: List[(String, DataType)] =
        (column -> headCodec.getCqlType) :: tailCodec.columns

      @inline override def encode(
          value: FieldType[K, H] :: T,
          protocolVersion: ProtocolVersion
      ): (List[ByteBuffer], Int) = {
        val (tailBuffer, tailSize) = tailCodec.encode(value.tail, protocolVersion)
        val encoded                = headCodec.encode(value.head, protocolVersion)
        val size                   = if (encoded == null) 4 else 4 + encoded.remaining()

        (encoded :: tailBuffer) -> (size + tailSize)
      }

      @inline override def decode(
          buffer: ByteBuffer,
          protocolVersion: ProtocolVersion
      ): FieldType[K, H] :: T =
        if (buffer == null) null.asInstanceOf[FieldType[K, H] :: T]
        else {
          val input = buffer.duplicate()

          val elementSize = input.getInt
          val element = if (elementSize < 0) {
            null.asInstanceOf[H]
          } else {
            val element = input.slice()
            element.limit(elementSize)

            headCodec.decode(element, protocolVersion)
          }

          (witness.value ->> element).asInstanceOf[FieldType[K, H]] :: tailCodec.decode(
            input.position(input.position() + Math.max(0, elementSize)),
            protocolVersion
          )
        }

      @inline override def format(
          value: FieldType[K, H] :: T,
          sb: mutable.StringBuilder
      ): mutable.StringBuilder = {
        val headFormat = sb
          .append(
            columnNamingScheme.asCql(fieldName, pretty = true)
          )
          .append(":")
          .append(
            if (value == null || value.head == null) NULL
            else headCodec.format(value.head)
          )
          .append(",")

        tailCodec.format(value.tail, headFormat)
      }

      @inline override def parse(value: String, idx: Int): (FieldType[K, H] :: T, Int) = {
        val start         = ParseUtils.skipSpaces(value, idx + 1)
        val (parsed, end) = parseWithCodec(value, headCodec, start)

        val next = ParseUtils.skipSpaces(value, end)
        if (next >= value.length) {
          throw new IllegalArgumentException(
            s"Malformed tuple value '$value', expected something else but got EOF"
          )
        }

        val (tail, nextTail) = tailCodec.parse(value, next)

        ((witness.value ->> parsed).asInstanceOf[FieldType[K, H]] :: tail) -> nextTail
      }
    }

  /** Generic Udt codec
    */
  implicit def genericUdtC[A, R](
      implicit gen: LabelledGeneric.Aux[A, R],
      codec: Lazy[IdenticalUDTCodec[R]]
  ): IdenticalUDTCodec[A] = new IdenticalUDTCodec[A] {

    override def columns: List[(String, DataType)] = codec.value.columns

    @inline override def encode(
        value: A,
        protocolVersion: ProtocolVersion
    ): (List[ByteBuffer], Int) =
      if (value == null) null
      else codec.value.encode(gen.to(value), protocolVersion)

    @inline override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): A =
      gen.from(codec.value.decode(buffer, protocolVersion))

    @inline override def format(value: A, sb: mutable.StringBuilder): mutable.StringBuilder =
      codec.value.format(gen.to(value), sb)

    @inline override def parse(value: String, idx: Int): (A, Int) = {
      val start = ParseUtils.skipSpaces(value, 0)

      expectParseChar(value, start, openingChar)
      val (parsed, end) = codec.value.parse(value, start)
      expectParseChar(value, end, closingChar)

      gen.from(parsed) -> end
    }
  }
}
