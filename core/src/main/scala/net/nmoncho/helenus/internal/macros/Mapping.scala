/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.reflect.ClassTag
import scala.reflect.macros.blackbox

import net.nmoncho.helenus.internal.cql.DerivedMapping
import net.nmoncho.helenus.internal.cql.DerivedMapping.Builder

object Mapping {

  /** Generates a [[net.nmoncho.helenus.api.cql.Mapping]] for a case class
    *
    * Allows users to map redefine how case class fields are mapped to columns, for fields
    * that cannot be mapped properly with [[net.nmoncho.helenus.api.ColumnNamingScheme]].
    *
    * This macro <em>doesn't</em> derive the [[net.nmoncho.helenus.api.cql.Mapping]], it just allows a convenient way
    * of defining the field/column remapping. The derivation comes from [[DerivedMapping]] implicit methods.
    *
    * @param renamedFields fields to be renamed. The implicit [[net.nmoncho.helenus.api.ColumnNamingScheme]] will be ignored for these fields
    * @param builder a function that given renamed fields will produce a [[net.nmoncho.helenus.api.cql.Mapping]]
    * @param classTag a [[ClassTag]] for the given case class
    * @param A
    * @tparam A case class type to generate a [[net.nmoncho.helenus.api.cql.Mapping]] for
    * @return a [[net.nmoncho.helenus.api.cql.Mapping]]
    */
  final def derivedMapping[A](c: blackbox.Context)(
      renamedFields: c.Expr[A => (Any, String)]*
  )(builder: c.Expr[Builder[A]], classTag: c.Expr[ClassTag[A]])(
      implicit A: c.WeakTypeTag[A]
  ): c.Expr[net.nmoncho.helenus.api.cql.Mapping[A]] = {
    import c.universe._

    // Verify `A` is not a tuple
    c.typecheck(q"_root_.shapeless.IsTuple[${A.tpe}]", silent = true) match {
      case EmptyTree => // all good
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed to have a Mapping, but got ${A.tpe}"
        )
    }

    // Verify `A` is a case class
    c.typecheck(q"implicitly[_root_.scala.<:<[${A.tpe}, scala.Product]]", silent = true) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed to have a Mapping, but got ${A.tpe}"
        )

      case _ => // all good
    }

    def findFieldName(expr: c.Expr[A => (Any, String)], paramName: String): String = expr.tree
      .collect { case Select(Ident(TermName(`paramName`)), TermName(fieldName)) =>
        fieldName
      }
      .headOption
      .getOrElse(c.abort(c.enclosingPosition, s"Cannot find fieldName for $paramName"))

    /** Transforms an expression like
      *
      * `(h: Hotel) => h.name -> "hotel_name"` to `"name" -> "hotel_name"`
      */
    def extract(expr: c.Expr[A => (Any, String)]): c.Expr[(String, String)] = {
      val paramName: String = expr.tree match {
        case Function(List(ValDef(_, TermName(name), _, _)), _) => name
      }
      val fieldName: String = findFieldName(expr, paramName)
      val mapping           = expr.tree
        .collect {
          case Apply(_, List(select @ Select(_))) => select
          case Apply(_, List(literal @ Literal(_))) => literal
        }
        .headOption
        .getOrElse(c.abort(c.enclosingPosition, s"Cannot find mapping for $paramName"))

      c.Expr[(String, String)](q"$fieldName -> $mapping")
    }

    val renamed = c.Expr[Map[String, String]](
      q"Map[String, String](..${renamedFields.map(r => extract(r))})"
    )

    reify(
      DerivedMapping[A](renamed.splice)(builder.splice, classTag.splice)
    )
  }

}
