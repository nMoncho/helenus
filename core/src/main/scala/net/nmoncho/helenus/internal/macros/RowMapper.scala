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

package net.nmoncho.helenus.internal.macros

import scala.reflect.macros.blackbox

import net.nmoncho.helenus.internal.DerivedRowMapper

object RowMapper {

  final def renamedMapper[D[x] <: DerivedRowMapper.Builder[x], A](
      c: blackbox.Context
  )(first: c.Expr[A => (Any, String)], rest: c.Expr[A => (Any, String)]*)(
      implicit D: c.WeakTypeTag[D[_]],
      A: c.WeakTypeTag[A]
  ): c.Expr[net.nmoncho.helenus.api.RowMapper[A]] = {
    import c.universe._

    // Verify `A` is only a case class
    c.typecheck(q"_root_.shapeless.IsTuple[${A.tpe}]", silent = true) match {
      case EmptyTree => // all good
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Only case classes are allowed with renamed RowMapper, but got ${A.tpe}"
        )
    }

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
      val mapping = expr.tree
        .collect {
          case Apply(_, List(select @ Select(_))) => select
          case Apply(_, List(literal @ Literal(_))) => literal
        }
        .headOption
        .getOrElse(c.abort(c.enclosingPosition, s"Cannot find mapping for $paramName"))

      c.Expr[(String, String)](
        q"$fieldName -> $mapping"
      )
    }

    val target = appliedType(D.tpe.typeConstructor, A.tpe)
    c.typecheck(q"_root_.shapeless.lazily[$target]", silent = true) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Unable to infer value of type $target. Probably a ColumnMapper is not implicitly available for at least one case class field."
        )
      case t =>
        c.Expr[DerivedRowMapper[A]](
          q"$t.apply(Map[String, String](${extract(first)}, ..${rest.map(r => extract(r))})): _root_.net.nmoncho.helenus.api.RowMapper[$A]"
        )
    }
  }

}
