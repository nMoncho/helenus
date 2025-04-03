/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.internal.codec.udt.NonIdenticalUDTCodec

object NonIdenticalCodec {

  /** Generates a [[TypeCodec]] for a case class, where the case class and the UDT
    * don't define fields in the same order.
    *
    * @param keyspace On which keyspace the UDT is defined. If empty string is used, session keyspace will be used
    * @param name UDT name. If empty case class Simple Name will be used
    * @param frozen whether the UDT is frozen in the table or not
    * @param fields what's the field order in the UDT
    * @param columnMapper how to map case class field names to UDT field names
    * @param classTag
    * @param A
    * @tparam A case class type to generate a [[net.nmoncho.helenus.api.cql.Mapping]] for
    * @return a [[TypeCodec]]
    */
  final def buildCodec[A](
      c: blackbox.Context
  )(keyspace: c.Expr[String], name: c.Expr[String], frozen: c.Expr[Boolean])(
      fields: c.Expr[A => Any]*
  )(
      columnMapper: c.Expr[ColumnNamingScheme],
      classTag: c.Expr[ClassTag[A]]
  )(implicit A: c.WeakTypeTag[A]): c.Expr[TypeCodec[A]] = {
    import c.universe._

    // Verify `A` is not a tuple
    c.typecheck(q"_root_.shapeless.IsTuple[${A.tpe}]", silent = true) match {
      case EmptyTree => // all good
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed to be used for UDTs, but got ${A.tpe}"
        )
    }

    // Verify `A` is a case class
    c.typecheck(
      q"implicitly[_root_.scala.<:<[${A.tpe}, _root_.scala.Product]]",
      silent = true
    ) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed to be used for UDTs, but got ${A.tpe}"
        )

      case _ => // all good
    }

    // Pick up Case Class field names and their respective TypeCodec
    val fieldsCodecs = fields.map { field =>
      val (select, name) = field.tree match {
        case Function(List(ValDef(_, _, _, _)), select @ Select(_, TermName(name))) =>
          select -> name
      }

      c.typecheck(
        q"implicitly[_root_.com.datastax.oss.driver.api.core.`type`.codec.TypeCodec[${select.tpe}]]",
        silent = true
      ) match {
        case EmptyTree =>
          c.abort(
            c.enclosingPosition,
            s"Couldn't find implicit TypeCodec for ${select.tpe}"
          )

        case codec =>
          name -> c.Expr[TypeCodec[_]](codec)
      }
    }

    // why does "Val" have a space at the end of the name?
    val expectedCaseClassFields = A.tpe.members
      .filter(_.asTerm.isCaseAccessor)
      .map(_.name.toString.trim)
      .toSet
    val fieldNames            = fieldsCodecs.map(_._1)
    val actualCaseClassFields = fieldNames.toSet

    // Verify that users have used all case class field
    if (expectedCaseClassFields != actualCaseClassFields) {
      val diff = expectedCaseClassFields -- actualCaseClassFields
      c.abort(
        c.enclosingPosition,
        s"The fields [${diff.mkString(", ")}] were missing when trying to generate a TypeCodec. All fields must be present"
      )
    }

    // Verify that there aren't duplicate fields
    if (fieldNames.size != actualCaseClassFields.size) {
      val offending = fieldNames.groupBy(identity).collect {
        case (name, count) if count.size > 1 =>
          name
      }
      c.abort(
        c.enclosingPosition,
        s"Fields cannot appear twice. Offending fields [${offending.mkString(", ")}]"
      )
    }

    val udtCodec = c.typecheck(
      q"implicitly[_root_.net.nmoncho.helenus.internal.codec.udt.NonIdenticalUDTCodec[${A.tpe}]]",
      silent = true
    ) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Couldn't create NonIdenticalUDTCodec ${A.tpe}. Verify that all case class fields have a corresponding a TypeCodec"
        )

      case codec =>
        c.Expr[NonIdenticalUDTCodec[A]](codec)
    }

    reify(
      udt(
        keyspace.splice,
        name.splice,
        frozen.splice,
        c.Expr[Seq[(String, TypeCodec[_])]](q"Seq(..$fieldsCodecs)").splice,
        columnMapper.splice,
        udtCodec.splice,
        classTag.splice
      )
    )
  }

  /** Creates a [[TypeCodec]]
    *
    * After the field order has been defined by the user, a [[TypeCodec]] is created
    * from a [[NonIdenticalUDTCodec]]
    */
  def udt[A](
      keyspace: String,
      name: String,
      frozen: Boolean,
      fields: Seq[(String, TypeCodec[_])],
      columnMapper: ColumnNamingScheme,
      codec: NonIdenticalUDTCodec[A],
      tag: ClassTag[A]
  ): TypeCodec[A] = {
    import scala.jdk.CollectionConverters._

    val actualKeyspace = if (keyspace.isBlank) "system" else keyspace
    val actualName = if (name.isBlank) columnMapper.map(tag.runtimeClass.getSimpleName) else name

    val fieldTypes = fields.map { case (_, codec) => codec.getCqlType }
    val fieldNames = fields.map { case (name, _) =>
      CqlIdentifier.fromInternal(columnMapper.map(name))
    }

    val udt = new DefaultUserDefinedType(
      CqlIdentifier.fromInternal(actualKeyspace),
      CqlIdentifier.fromInternal(actualName),
      frozen,
      fieldNames.asJava,
      fieldTypes.asJava
    )

    NonIdenticalUDTCodec[A](udt)(codec, tag)
  }
}
