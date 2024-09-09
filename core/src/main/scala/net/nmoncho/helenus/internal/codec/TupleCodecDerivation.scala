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

import java.nio.ByteBuffer

import scala.collection.mutable

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.internal.core.`type`.DefaultTupleType
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

trait TupleCodecDerivation {

  import shapeless._

  trait TupleCodec[T] extends TypeCodec[T]

  def tupleOf[T: IsTuple](implicit c: TupleCodec[T]): TypeCodec[T] = c

  /** Adapter class for tuples' [[TypeCodec]]
    *
    * @tparam T tuple type
    */
  trait TupleComponentCodec[T] {

    protected val separator = ','

    /** Encodes value `T` as a [[ByteBuffer]], and appends it to a list
      *
      * @param value value to be encoded
      * @param protocolVersion which DSE version to use
      * @return accumulated buffers, one per tuple component, with the total buffer size
      */
    @inline def encode(value: T, protocolVersion: ProtocolVersion): (List[ByteBuffer], Int)

    /** Decodes a value `T` from a [[ByteBuffer]]
      */
    @inline def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): T

    /** List of [[TupleComponentCodec]], one per tuple component.
      * Used internally for tasks that don't require type safety
      */
    private[codec] def codecs: Seq[TypeCodec[_]]

    /** Formats a tuple component into a [[mutable.StringBuilder]]
      *
      * @param value component to format
      * @param sb format target
      * @return modified target (since it's mutable this isn't required)
      */
    @inline private[codec] def format(value: T, sb: mutable.StringBuilder): mutable.StringBuilder

    /** Parses a tuple component, returning also the next index where to continue with the parse.
      */
    @inline private[codec] def parse(value: String, idx: Int): (T, Int)
  }

  /** Codec for last element in the [[HList]]
    * It's defined this way so we don't have to handle [[HNil]]
    *
    * @param codec [[Codec]] for type `H`
    */
  implicit def lastTupleElementCodec[H](
      implicit codec: TypeCodec[H]
  ): TupleComponentCodec[H :: HNil] =
    new TupleComponentCodec[H :: HNil] {
      override private[codec] def codecs: Seq[TypeCodec[_]] = Vector(codec)

      @inline override def encode(
          value: H :: HNil,
          protocolVersion: ProtocolVersion
      ): (List[ByteBuffer], Int) = {
        val encoded = codec.encode(value.head, protocolVersion)
        val size    = if (encoded == null) 4 else 4 + encoded.remaining()

        List(encoded) -> size
      }

      @inline override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): H :: HNil =
        if (buffer == null) null.asInstanceOf[H :: HNil]
        else {
          val input = buffer.duplicate()

          val elementSize = input.getInt
          val element = if (elementSize < 0) {
            codec.decode(null, protocolVersion)
          } else {
            val element = input.slice()
            element.limit(elementSize)
            input.position(input.position() + elementSize)

            codec.decode(element, protocolVersion)
          }

          element :: HNil
        }

      @inline override private[codec] def format(
          value: H :: HNil,
          sb: mutable.StringBuilder
      ): mutable.StringBuilder =
        sb.append(
          if (value == null || value.head == null) NULL
          else codec.format(value.head)
        )

      @inline override private[codec] def parse(value: String, idx: Int): (H :: HNil, Int) = {
        val (parsed, next) = parseElementWithCodec(codec, value, idx)

        (parsed :: HNil) -> next
      }
    }

  /** Codec for a head component in a [[HList]]
    */
  implicit def hListTupleCodec[H, T <: HList](
      implicit headCodec: TypeCodec[H],
      tailCodec: TupleComponentCodec[T]
  ): TupleComponentCodec[H :: T] = new TupleComponentCodec[H :: T] {
    override private[codec] def codecs: Seq[TypeCodec[_]] = headCodec +: tailCodec.codecs

    @inline override def encode(
        value: H :: T,
        protocolVersion: ProtocolVersion
    ): (List[ByteBuffer], Int) = {
      val (tailBuffer, tailSize) = tailCodec.encode(value.tail, protocolVersion)
      val encoded                = headCodec.encode(value.head, protocolVersion)
      val size                   = if (encoded == null) 4 else 4 + encoded.remaining()

      (encoded :: tailBuffer) -> (size + tailSize)
    }

    @inline override def decode(buffer: ByteBuffer, protocolVersion: ProtocolVersion): H :: T =
      if (buffer == null) null.asInstanceOf[H :: T]
      else {
        val input = buffer.duplicate()

        val elementSize = input.getInt
        val element = if (elementSize < 0) {
          headCodec.decode(null, protocolVersion)
        } else {
          val element = input.slice()
          element.limit(elementSize)

          headCodec.decode(element, protocolVersion)
        }

        element :: tailCodec.decode(
          input.position(input.position() + Math.max(0, elementSize)),
          protocolVersion
        )
      }

    @inline override private[codec] def format(
        value: H :: T,
        sb: mutable.StringBuilder
    ): mutable.StringBuilder =
      if (value == null) sb.append(NULL)
      else {
        tailCodec.format(
          value.tail,
          sb.append(headCodec.format(value.head)).append(separator)
        )
      }

    @inline override private[codec] def parse(value: String, idx: Int): (H :: T, Int) = {
      val (parsed, next)   = parseElementWithCodec(headCodec, value, idx)
      val (tail, nextTail) = tailCodec.parse(value, next)

      (parsed :: tail) -> nextTail
    }
  }

  import scala.reflect.runtime.universe._

  /** Derives a [[TypeCodec]] for a tuple of type [[A]].
    *
    * A [[TypeTag]] is used to create a [[GenericType]] instance.
    */
  implicit def tupleCodec[A: IsTuple: TypeTag, R](
      implicit gen: Generic.Aux[A, R],
      codec: TupleComponentCodec[R]
  ): TupleCodec[A] = new TupleCodec[A] {

    private val openingChar = '('
    private val closingChar = ')'

    private lazy val codecs = codec.codecs

    override val getJavaType: GenericType[A] =
      GenericType
        .of(new TypeAdapter(implicitly[TypeTag[A]].tpe))
        .asInstanceOf[GenericType[A]]

    override val getCqlType: DataType = {
      import scala.jdk.CollectionConverters._

      new DefaultTupleType(codecs.map(_.getCqlType).asJava)
    }

    override def encode(value: A, protocolVersion: ProtocolVersion): ByteBuffer =
      if (value == null) null
      else {
        val (buffers, size) = codec.encode(gen.to(value), protocolVersion)
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
      if (buffer == null) null.asInstanceOf[A]
      else gen.from(codec.decode(buffer, protocolVersion))

    override def format(value: A): String =
      if (value == null) NULL
      else {
        val sb = new mutable.StringBuilder()
        sb.append(openingChar)
        codec.format(gen.to(value), sb)
        sb.append(closingChar)

        sb.toString()
      }

    override def parse(value: String): A =
      if (value == null || value.isEmpty || value.equalsIgnoreCase(NULL)) {
        null.asInstanceOf[A]
      } else {
        val start = ParseUtils.skipSpaces(value, 0)

        expectParseChar(value, start, openingChar)
        val (parsed, end) = codec.parse(value, start)
        expectParseChar(value, end, closingChar)

        gen.from(parsed)
      }

    override def accepts(value: Any): Boolean = value match {
      // FIXME This can still accept case classes with the same component types
      case product: Product if product.productArity == codecs.size =>
        product.productIterator.zip(codecs.iterator).forall { case (element, codec) =>
          codec.accepts(element)
        }

      case _ =>
        false
    }

    override lazy val toString: String = s"TupleCodec[(${codecs.map(_.getCqlType).mkString(", ")})]"
  }

  /** Parses a tuple component, from a starting position.
    *
    * @param codec codec to use for parsing
    * @param value entire string to parse from
    * @param idx index where to start parsing of this specific tuple component
    * @tparam T tuple component type
    * @return tuple element, and next parsing position
    */
  private[codec] def parseElementWithCodec[T](
      codec: TypeCodec[T],
      value: String,
      idx: Int
  ): (T, Int) = {
    val start         = ParseUtils.skipSpaces(value, idx + 1)
    val (parsed, end) = parseWithCodec(value, codec, start)

    val next = ParseUtils.skipSpaces(value, end)
    if (next >= value.length) {
      throw new IllegalArgumentException(
        s"Malformed tuple value '$value', expected something else but got EOF"
      )
    }

    parsed -> next
  }

  // $COVERAGE-OFF$
  /** [[java.lang.reflect.Type]] implementation using an underlying Scala Reflect [[Type]]
    * This is only used for [[com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken]] and [[GenericType]] for tuples
    *
    * <b>Note</b>: I couldn't achieve the same result with [[GenericType]], but could be something to take a look at later.
    */
  private class TypeAdapter(private val tpe: Type) extends java.lang.reflect.Type {
    override def getTypeName: String = tpe.toString

    override def hashCode(): Int = tpe.hashCode()

    override def equals(obj: Any): Boolean = obj match {
      case other: TypeAdapter =>
        (other eq this) || other.tpe == tpe

      case _ =>
        false
    }
  }
  // $COVERAGE-ON$
}
