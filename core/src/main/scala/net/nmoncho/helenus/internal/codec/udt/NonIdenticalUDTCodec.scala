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
package internal.codec.udt

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.internal.core.`type`.codec.{ UdtCodec => DseUdtCodec }
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps

/** A [[NonIdenticalUDTCodec]] is one that maps a case class to a UDT, both not having the same
  * field order, for example:
  *
  * {{{
  * case class IceCream(name: String, numCherries: Int, cone: Boolean)
  *
  * TYPE ice_cream(cone BOOLEAN, name TEXT, num_cherries INT)
  * }}}
  *
  * For case classes and UDTs that align on the field order, please use [[IdenticalUDTCodec]]. See our
  * Developer Notes for more information.
  *
  * This trait follows [[MappingCodec]] interface closely, as that's how it implemented in the end.
  * The only difference is that `outerToInner` is "tail-rec" to handle shapeless encoding.
  *
  * @tparam A case class being encoded
  */
trait NonIdenticalUDTCodec[A] {

  /** Maps a [[UdtValue]] into an [[A]]
    *
    * @param value value coming from the database
    * @return mapped [[A]] value
    */
  @inline def innerToOuter(value: UdtValue): A

  /** Maps a value [[A]] into a [[UdtValue]]
    *
    * @param udt value used to accumulate all fields from [[A]]
    * @param value value going to the database
    * @return [[UdtValue]] containing all mapped values from [[A]]
    */
  @inline def outerToInner(udt: UdtValue, value: A): UdtValue
}

object NonIdenticalUDTCodec {
  import shapeless._

  /** Creates [[TypeCodec]] for [[A]].
    *
    * Use this method when case class and UDT don't have the same field order. Otherwise use [[IdenticalUDTCodec]]
    *
    * @param session needed to query the actual shape of the CQL Type
    * @param keyspace where is the CQL Type defined. If left empty, the session's keyspace will be used.
    * @param name CQL Type name. If left empty, [[columnNamingScheme]] will be used to derive name for the case class name
    *
    * @param codec implicit codec
    * @param tag implicit class tag
    * @param columnNamingScheme how to map field names between CQL Type and Case Class.
    *
    * @tparam A case class type
    * @return [[TypeCodec]] for [[A]]
    */
  def apply[A](session: CqlSession, keyspace: String, name: String)(
      implicit codec: NonIdenticalUDTCodec[A],
      tag: ClassTag[A],
      columnNamingScheme: ColumnNamingScheme = DefaultColumnNamingScheme
  ): TypeCodec[A] = {
    import scala.jdk.OptionConverters._

    val clazz = tag.runtimeClass.asInstanceOf[Class[A]]

    val actualName =
      if (name.isBlank) columnNamingScheme.map(clazz.getSimpleName)
      else name

    val actualMetadata = Option(keyspace)
      .filter(_.trim.nonEmpty)
      .flatMap(session.keyspace)
      .orElse(
        session.sessionKeyspace
      )

    // if all information is available, create `MappingCodec`
    (for {
      actualKeyspace <- actualMetadata
      udt <- actualKeyspace.getUserDefinedType(actualName).toScala
      dseCodec    = new DseUdtCodec(udt)
      genericType = GenericType.of(clazz)
    } yield new MappingCodec[UdtValue, A](dseCodec, genericType) with UDTCodec[A] {

      override val getCqlType: UserDefinedType =
        super.getCqlType.asInstanceOf[UserDefinedType]

      override def innerToOuter(value: UdtValue): A =
        codec.innerToOuter(value)

      override def outerToInner(value: A): UdtValue =
        codec.outerToInner(getCqlType.newValue(), value)

      override lazy val toString: String =
        s"UtdCodec[${clazz.getSimpleName}]"
    }).getOrElse {
      throw new IllegalArgumentException(
        s"Cannot create TypeCodec for ${clazz}. Couldn't find type [$actualName] in keyspace [$actualMetadata]"
      )
    }
  }

  def apply[A](
      udt: UserDefinedType
  )(implicit codec: NonIdenticalUDTCodec[A], tag: ClassTag[A]): TypeCodec[A] = {
    val clazz       = tag.runtimeClass.asInstanceOf[Class[A]]
    val dseCodec    = new DseUdtCodec(udt)
    val genericType = GenericType.of(clazz)

    new MappingCodec[UdtValue, A](dseCodec, genericType) with UDTCodec[A] {

      override val getCqlType: UserDefinedType =
        super.getCqlType.asInstanceOf[UserDefinedType]

      override def accepts(cqlType: DataType): Boolean = cqlType match {
        case aUDT: UserDefinedType =>
          udt.getFieldNames == aUDT.getFieldNames && udt.getFieldTypes == aUDT.getFieldTypes

        case _ => false
      }

      override def innerToOuter(value: UdtValue): A =
        codec.innerToOuter(value)

      override def outerToInner(value: A): UdtValue =
        codec.outerToInner(getCqlType.newValue(), value)

      override lazy val toString: String =
        s"UtdCodec[${clazz.getSimpleName}]"
    }
  }

  /** Last UDT/CaseClass element
    */
  implicit def lastUdtElementCodec[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): NonIdenticalUDTCodec[FieldType[K, H] :: HNil] =
    new NonIdenticalUDTCodec[FieldType[K, H] :: HNil] {
      private val column = columnMapper.map(witness.value.name)

      @inline override def innerToOuter(value: UdtValue): FieldType[K, H] :: HNil =
        (witness.value ->> value.get(column, codec)).asInstanceOf[FieldType[K, H]] :: HNil

      @inline override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: HNil): UdtValue =
        udt.set(column, value.head, codec)
    }

  /** HList UDT/CaseClass element
    */
  implicit def hListUdtCodec[K <: Symbol, H, T <: HList](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      tailCodec: NonIdenticalUDTCodec[T],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): NonIdenticalUDTCodec[FieldType[K, H] :: T] =
    new NonIdenticalUDTCodec[FieldType[K, H] :: T] {

      private val column = columnMapper.map(witness.value.name)

      @inline override def innerToOuter(value: UdtValue): FieldType[K, H] :: T =
        (witness.value ->> value.get(column, codec))
          .asInstanceOf[FieldType[K, H]] :: tailCodec.innerToOuter(value)

      @inline override def outerToInner(udt: UdtValue, value: FieldType[K, H] :: T): UdtValue =
        tailCodec.outerToInner(
          udt.set(column, value.head, codec),
          value.tail
        )
    }

  /** Generic Udt codec
    */
  implicit def genericUdtCodec[A, R](
      implicit generic: LabelledGeneric.Aux[A, R],
      codec: Lazy[NonIdenticalUDTCodec[R]],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): NonIdenticalUDTCodec[A] = new NonIdenticalUDTCodec[A] {

    @inline override def innerToOuter(value: UdtValue): A =
      generic.from(codec.value.innerToOuter(value))

    @inline override def outerToInner(udt: UdtValue, value: A): UdtValue =
      codec.value.outerToInner(udt, generic.to(value))
  }
}
