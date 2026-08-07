/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec
package udt

import java.nio.ByteBuffer

import scala.collection.mutable
import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils
import net.nmoncho.helenus.api.ColumnNamingScheme
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

  /** Encodes an [[A]] value, writing each field's [[ByteBuffer]] into `buffer`.
    *
    * Mirrors the collection codec's near-zero-intermediate-allocation encode: instead of
    * returning a `(List[ByteBuffer], Int)` at each level (which allocates a cons cell and a
    * `Tuple2` per field), the shapeless recursion writes into a shared, pre-sized array and
    * accumulates the total payload size as an `Int`.
    *
    * @param value           value to be encoded
    * @param protocolVersion DSE Version in use
    * @param buffer          pre-sized array receiving one encoded buffer per case class component
    * @param index           index in `buffer` at which to write this value's first field
    * @return the accumulated payload size (in bytes) of the fields written
    */
  @inline def encode(
      value: A,
      protocolVersion: ProtocolVersion,
      buffer: Array[ByteBuffer],
      index: Int
  ): Int

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

  private val openingChar    = '{'
  private val fieldSeparator = ':'
  private val separator      = ','
  private val closingChar    = '}'

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
      columnNamingScheme: ColumnNamingScheme = ColumnNamingScheme.Default
  ): TypeCodec[A] = new TypeCodec[A] with UDTCodec[A] {

    private val actualKeyspace =
      if (keyspace.isBlank) "system"
      else keyspace

    private val actualName =
      if (name.isBlank) columnNamingScheme.apply(tag.runtimeClass.getSimpleName)
      else name

    // Number of case class components, computed once so `encode` can pre-size its array.
    private val fieldCount: Int = codec.columns.size

    override val getJavaType: GenericType[A] =
      GenericType.of(tag.runtimeClass.asInstanceOf[Class[A]])

    override val getCqlType: UserDefinedType = {
      import scala.jdk.CollectionConverters._

      val (identifiers, dataTypes) =
        codec.columns.foldRight(List.empty[CqlIdentifier] -> List.empty[DataType]) {
          case ((name, dataType), (identifiers, dataTypes)) =>
            (CqlIdentifier.fromInternal(name) :: identifiers) -> (dataType :: dataTypes)
        }

      new DefaultUserDefinedType(
        CqlIdentifier.fromInternal(actualKeyspace),
        CqlIdentifier.fromInternal(actualName),
        frozen,
        identifiers.asJava,
        dataTypes.asJava
      )
    }

    override def accepts(cqlType: DataType): Boolean = cqlType match {
      case udt: UserDefinedType =>
        udt.getFieldNames == getCqlType.getFieldNames &&
        udt.getFieldTypes == getCqlType.getFieldTypes

      case _ => false
    }

    override def encode(value: A, protocolVersion: ProtocolVersion): ByteBuffer =
      if (value == null) null
      else {
        // Pre-size a plain array (matching the collection codec) instead of building a List
        // via the shapeless recursion, so there is no per-field cons-cell or Tuple2 allocation.
        // The recursion writes each field's buffer into `buffers` and returns the payload size.
        val buffers = new Array[ByteBuffer](fieldCount)
        val size    = codec.encode(value, protocolVersion, buffers, 0)
        val result  = ByteBuffer.allocate(size)

        var i = 0
        while (i < fieldCount) {
          val field = buffers(i)
          if (field == null) result.putInt(-1)
          else {
            result.putInt(field.remaining())
            result.put(field)
          }
          i += 1
        }

        result.flip()
        result // return the buffer explicitly; safe across JDK 8 (flip returns Buffer) and 9+
      }

    override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): A =
      if (buffer == null) null.asInstanceOf[A]
      else codec.decode(buffer, protocolVersion)

    override def format(value: A): String =
      if (value == null) NULL
      else {
        val sb = new mutable.StringBuilder().append(openingChar)

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
      columnNamingScheme: ColumnNamingScheme = ColumnNamingScheme.Default
  ): IdenticalUDTCodec[FieldType[K, H] :: HNil] = new IdenticalUDTCodec[FieldType[K, H] :: HNil] {

    private val fieldName: String = witness.value.name
    private val column: String    = columnNamingScheme.apply(fieldName)

    override val columns: List[(String, DataType)] = List(column -> codec.getCqlType)

    @inline override def encode(
        value: FieldType[K, H] :: HNil,
        protocolVersion: ProtocolVersion,
        buffer: Array[ByteBuffer],
        index: Int
    ): Int = {
      val encoded = codec.encode(value.head, protocolVersion)
      buffer(index) = encoded

      if (encoded == null) 4 else 4 + encoded.remaining()
    }

    @inline override def decode(
        buffer: ByteBuffer,
        protocolVersion: ProtocolVersion
    ): FieldType[K, H] :: HNil =
      if (buffer == null) null.asInstanceOf[FieldType[K, H] :: HNil]
      else {
        val input = buffer.duplicate()

        val elementSize = input.getInt
        val element     = if (elementSize < 0) {
          codec.decode(null, protocolVersion)
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
      ).append(fieldSeparator)
        .append(
          if (value == null || value.head == null) NULL
          else codec.format(value.head)
        )

    @inline override def parse(value: String, idx: Int): (FieldType[K, H] :: HNil, Int) = {
      val fieldNameEnd =
        skipSpacesAndExpectId(value, idx, columnNamingScheme.asCql(fieldName, pretty = true))
      val valueStart     = skipSpacesAndExpect(value, fieldNameEnd, fieldSeparator)
      val (parsed, next) = parseWithCodec(value, codec, valueStart)

      ((witness.value ->> parsed).asInstanceOf[FieldType[K, H]] :: HNil) -> next
    }
  }

  /** HList UDT/CaseClass element
    */
  implicit def hListUdtCComponent[K <: Symbol, H, T <: HList](
      implicit headCodec: TypeCodec[H],
      witness: Witness.Aux[K],
      tailCodec: IdenticalUDTCodec[T],
      columnNamingScheme: ColumnNamingScheme = ColumnNamingScheme.Default
  ): IdenticalUDTCodec[FieldType[K, H] :: T] =
    new IdenticalUDTCodec[FieldType[K, H] :: T] {

      private val fieldName = witness.value.name
      private val column    = columnNamingScheme.apply(fieldName)

      override val columns: List[(String, DataType)] =
        (column -> headCodec.getCqlType) :: tailCodec.columns

      @inline override def encode(
          value: FieldType[K, H] :: T,
          protocolVersion: ProtocolVersion,
          buffer: Array[ByteBuffer],
          index: Int
      ): Int = {
        val encoded = headCodec.encode(value.head, protocolVersion)
        buffer(index) = encoded
        val size = if (encoded == null) 4 else 4 + encoded.remaining()

        size + tailCodec.encode(value.tail, protocolVersion, buffer, index + 1)
      }

      @inline override def decode(
          buffer: ByteBuffer,
          protocolVersion: ProtocolVersion
      ): FieldType[K, H] :: T =
        if (buffer == null) null.asInstanceOf[FieldType[K, H] :: T]
        else {
          val input = buffer.duplicate()

          val elementSize = input.getInt
          val element     = if (elementSize < 0) {
            headCodec.decode(null, protocolVersion)
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
          .append(columnNamingScheme.asCql(fieldName, pretty = true))
          .append(fieldSeparator)
          .append(
            if (value == null || value.head == null) NULL
            else headCodec.format(value.head)
          )
          .append(separator)

        tailCodec.format(value.tail, headFormat)
      }

      @inline override def parse(value: String, idx: Int): (FieldType[K, H] :: T, Int) = {
        val fieldNameEnd =
          skipSpacesAndExpectId(value, idx, columnNamingScheme.asCql(fieldName, pretty = true))
        val valueStart         = skipSpacesAndExpect(value, fieldNameEnd, fieldSeparator)
        val (parsed, valueEnd) = parseWithCodec(value, headCodec, valueStart)
        val afterValue         = skipSpacesAndExpect(value, valueEnd, separator)

        val (tail, nextTail) = tailCodec.parse(value, afterValue)

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
        protocolVersion: ProtocolVersion,
        buffer: Array[ByteBuffer],
        index: Int
    ): Int =
      codec.value.encode(gen.to(value), protocolVersion, buffer, index)

    @inline override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): A =
      if (buffer == null) null.asInstanceOf[A]
      else gen.from(codec.value.decode(buffer, protocolVersion))

    @inline override def format(value: A, sb: mutable.StringBuilder): mutable.StringBuilder =
      codec.value.format(gen.to(value), sb)

    @inline override def parse(value: String, idx: Int): (A, Int) = {
      val start = ParseUtils.skipSpaces(value, 0)

      expectParseChar(value, start, openingChar)
      val (parsed, end) = codec.value.parse(value, start + 1)
      expectParseChar(value, end, closingChar)

      gen.from(parsed) -> end
    }
  }
}
