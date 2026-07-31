/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.reflect.macros.blackbox

import net.nmoncho.helenus.internal.DerivedRowMapper

object RowMapper {

  final def derivedColumnMapper[D[x] <: DerivedRowMapper.Builder[x], A](
      c: blackbox.Context
  )(prefix: c.Expr[String], renamedFields: c.Expr[A => (Any, String)]*)(
      implicit D: c.WeakTypeTag[D[_]],
      A: c.WeakTypeTag[A]
  ): c.Expr[net.nmoncho.helenus.api.RowMapper.ColumnMapper[A]] = {
    import c.universe._

    val naming = c.typecheck(
      q"implicitly[_root_.net.nmoncho.helenus.api.ColumnNamingScheme]",
      silent = true
    ) match {
      case EmptyTree => q"_root_.net.nmoncho.helenus.api.ColumnNamingScheme.Default"
      case n => n
    }

    val (derived, fields, renames) = validateAndExtract[D, A](c)(renamedFields)

    c.Expr[net.nmoncho.helenus.api.RowMapper.ColumnMapper[A]](
      q"""new _root_.net.nmoncho.helenus.api.RowMapper.ColumnMapper[$A] {
          private val original: Map[String, String] = $fields.map(f => f -> ($prefix + $naming(f))).toMap
          private val renamed: Map[String, String] = Map[String, String](..$renames)
          private val mapper: _root_.net.nmoncho.helenus.api.RowMapper[$A] = $derived.apply(original ++ renamed)

          override def apply(columnName: _root_.java.lang.String, row: _root_.com.datastax.oss.driver.api.core.cql.Row): $A =
            mapper(row)
        }
       """
    )
  }

  final def renamedMapper[D[x] <: DerivedRowMapper.Builder[x], A](
      c: blackbox.Context
  )(renamedFields: c.Expr[A => (Any, String)]*)(
      implicit D: c.WeakTypeTag[D[_]],
      A: c.WeakTypeTag[A]
  ): c.Expr[net.nmoncho.helenus.api.RowMapper[A]] = {
    import c.universe._

    val (derived, _, renames) = validateAndExtract[D, A](c)(renamedFields)

    c.Expr[DerivedRowMapper[A]](
      q"$derived.apply(Map[String, String](..$renames)): _root_.net.nmoncho.helenus.api.RowMapper[$A]"
    )
  }

  private def validateAndExtract[D[x] <: DerivedRowMapper.Builder[x], A](
      c: blackbox.Context
  )(renamedFields: Seq[c.Expr[A => (Any, String)]])(
      implicit D: c.WeakTypeTag[D[_]],
      A: c.WeakTypeTag[A]
  ): (c.universe.Tree, List[String], Seq[c.Expr[(String, String)]]) = {
    import c.universe._

    // Verify `A` is not a tuple
    c.typecheck(q"_root_.shapeless.IsTuple[${A.tpe}]", silent = true) match {
      case EmptyTree => // all good
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed with renamed RowMapper, but got ${A.tpe}"
        )
    }

    // Verify `A` is a case class
    c.typecheck(q"implicitly[_root_.scala.<:<[${A.tpe}, scala.Product]]", silent = true) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed with renamed RowMapper, but got ${A.tpe}"
        )
      case _ => // all good
    }

    def findFieldName(expr: c.Expr[A => (Any, String)], paramName: String): String = expr.tree
      .collect { case Select(Ident(TermName(`paramName`)), TermName(fieldName)) =>
        fieldName
      }
      .headOption
      .getOrElse(c.abort(c.enclosingPosition, s"Cannot find fieldName for $paramName"))

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

    // All fields of the case class, in primary-constructor order.
    val fieldNames: List[String] = A.tpe.decls
      .collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m }
      .getOrElse(c.abort(c.enclosingPosition, s"${A.tpe} has no primary constructor"))
      .paramLists
      .head
      .map(_.name.decodedName.toString)

    val target = appliedType(D.tpe.typeConstructor, A.tpe)

    // Validate that the instance resolves, but throw the typed tree away.
    c.typecheck(q"_root_.shapeless.lazily[$target]", silent = true) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Unable to infer value of type $target. Probably a ColumnMapper is not implicitly available for at least one case class field."
        )
      case _ => // resolves fine
    }

    // Splice this UNTYPED tree instead of the typechecked result.
    val derived = q"_root_.shapeless.lazily[$target]"

    (derived, fieldNames, renamedFields.map(extract))
  }
}
