/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.util.Strings
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.api.cql.WrappedBoundStatement
import net.nmoncho.helenus.internal.cql.CqlValidator

object CqlQueryInterpolation {

  def toCQL(c: blackbox.Context)(session: c.Expr[CqlSession]): c.Expr[CQLQuery] = {
    import c.universe._

    // c.prefix is Apply(CqlStringOps, List(innerExpr)); innerExpr may be a literal or
    // a chain of constant-folding calls (.stripMargin, .trim, …) wrapping a literal.
    c.prefix.tree match {
      case Apply(_, List(inner)) =>
        evalString(c)(inner) match {
          case None =>
            c.abort(c.enclosingPosition, "toCQL requires a compile-time constant string")

          case Some(cql) =>
            CqlValidator.validate(cql) match {
              case Right(_) =>
                c.Expr[CQLQuery](
                  q"new _root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery($cql, $session)"
                )

              case Left((error, pos)) =>
                val site = literalPos(c)(inner)
                  .map(p => p.withPoint(p.point + pos))
                  .getOrElse(c.enclosingPosition)

                c.abort(site, s"Invalid CQL: $error")
            }
        }
      case _ =>
        c.abort(c.enclosingPosition, "toCQL requires a string literal")
    }
  }

  def cql(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(session: c.Expr[CqlSession]): c.Expr[WrappedBoundStatement[Row]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    CqlValidator.validate(stmt) match {
      case Right(_) => // do nothing, all good
      case Left((error, pos)) =>
        c.abort(
          c.enclosingPosition.withPoint(c.enclosingPosition.point + pos),
          s"Invalid CQL: $error"
        )
    }

    val bstmt = c.Expr[BoundStatement](
      q"$session.prepare($stmt).bind()"
    )

    val expr = bindParameters.foldLeft(bstmt) { case (stmt, parameter) =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[BoundStatement](
          q"$stmt.set(${parameter.tree.symbol.name.toString}, $parameter, $codec)"
        )
      }
    }

    c.Expr[WrappedBoundStatement[Row]](
      q"new _root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement($expr)(_root_.net.nmoncho.helenus.api.RowMapper.identity)"
    )
  }

  def cqlAsync(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[Future[CqlSession]],
      ec: c.Expr[ExecutionContext]
  ): c.Expr[Future[WrappedBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    CqlValidator.validate(stmt) match {
      case Right(_) => // do nothing, all good
      case Left((error, pos)) =>
        c.abort(
          c.enclosingPosition.withPoint(c.enclosingPosition.point + pos),
          s"Invalid CQL: $error"
        )
    }

    val pstmt = c.Expr[Future[PreparedStatement]](
      q"$session.flatMap(s => _root_.net.nmoncho.helenus.internal.compat.FutureConverters.asScala(s.prepareAsync($stmt)))"
    )

    val bounders = bindParameters.map { parameter =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[Unit](
          q"bstmt = bstmt.set(${parameter.tree.symbol.name.toString}, $parameter, $codec)"
        )
      }
    }

    val expr = c.Expr[Future[WrappedBoundStatement[Row]]](
      q"$pstmt.map { stmt => var bstmt = stmt.bind(); ..$bounders; new _root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement(bstmt)(_root_.net.nmoncho.helenus.api.RowMapper.identity)}($ec)"
    )

    expr
  }

  /** Builds a CQL Statement, considering:
    *   - Constants will be replaced as is
    *   - Other expressions will be considered 'Named Bound Parameters'
    *
    * @param c Context
    * @param params String Interpolated parameters
    * @return CQL statement, with the expression that are meant to be Bound Parameters
    */
  private def buildStatement(
      c: blackbox.Context
  )(params: Seq[c.Expr[Any]]): (String, Seq[c.Expr[Any]]) = {
    import c.universe._

    // All parts a String constants
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map { case Literal(Constant(value: String)) => value }
    }

    // Params that are constants will be replaced as is,
    // whereas other are defined as named bind parameters
    val paramsTokens = params.map { expr =>
      expr.tree match {
        case Literal(Constant(value)) =>
          value

        case other =>
          val symbol = other.symbol.name.toString
          val named  =
            if (Strings.needsDoubleQuotes(symbol)) Strings.doubleQuote(symbol)
            else symbol

          s":$named"
      }
    }

    val cql = parts
      .zip(paramsTokens)
      .foldLeft(new StringBuilder()) { case (acc, (part, param)) =>
        acc.append(part).append(param)
      }
      .append(parts.lastOption.getOrElse(""))
      .toString

    val bindParameters = params.filter {
      _.tree match {
        case Literal(Constant(_)) => false
        case _ => true
      }
    }

    cql -> bindParameters
  }

  /** Sets a Bind Parameter into a CQL BoundStatement
    *
    * @param c Context
    * @param param parameter to set
    * @param bind function called to set the parameter with a given codec
    * @tparam A resulting expression type
    * @return resulting expression
    */
  private def setBindParameter[A](
      c: blackbox.Context
  )(param: c.Expr[Any])(bind: c.Tree => c.Expr[A]): c.Expr[A] = {
    import c.universe._

    c.typecheck(
      q"implicitly[_root_.com.datastax.oss.driver.api.core.`type`.codec.TypeCodec[${param.tree.tpe}]]",
      silent = true
    ) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Couldn't find an implicit TypeCodec for [${param.tree.tpe}]"
        )

      case codec =>
        bind(codec)
    }
  }

  // ---------------------------------------------------------------------------
  // Compile-time string evaluation
  // ---------------------------------------------------------------------------

  // Recursively evaluates a compile-time constant String expression, mirroring
  // common constant-folding chains (.stripMargin, .trim, …).
  //
  // The tricky case: String.stripMargin/trim live on StringOps, not String, so
  // the typed tree goes through scala.Predef.augmentString before the method
  // call.  The resulting shape is:
  //   Select(Apply(augmentString, List(lit)), TermName("stripMargin"))
  // rather than Apply(Select(lit, "stripMargin"), Nil).
  // We handle this by (a) recognising the method names as Select nodes and
  // (b) treating any single-arg Apply as a transparent wrapper and recursing
  // into its argument — this covers augmentString and similar identity lifts.
  private def evalString(c: blackbox.Context)(tree: c.universe.Tree): Option[String] = {
    import c.universe._
    tree match {
      case Literal(Constant(s: String)) =>
        Some(s)

      // Zero-arg methods represented as Select (no () in source, typed tree omits Apply)
      case Select(receiver, TermName("stripMargin")) =>
        evalString(c)(receiver).map(_.stripMargin)

      case Select(receiver, TermName("trim")) =>
        evalString(c)(receiver).map(_.trim)

      // Same methods called with explicit ()
      case Apply(Select(receiver, TermName("stripMargin")), Nil) =>
        evalString(c)(receiver).map(_.stripMargin)

      case Apply(Select(receiver, TermName("trim")), Nil) =>
        evalString(c)(receiver).map(_.trim)

      // stripMargin(marginChar)
      case Apply(Select(receiver, TermName("stripMargin")), List(Literal(Constant(ch: Char)))) =>
        evalString(c)(receiver).map(_.stripMargin(ch))

      // Single-arg application: transparent wrapper (e.g. Predef.augmentString).
      // Recurse into the wrapped value — if it's not a constant we return None.
      case Apply(_, List(inner)) =>
        evalString(c)(inner)

      case _ =>
        None
    }
  }

  // Returns the position of the innermost string Literal adjusted to the first
  // content character (past the opening quote(s)), used for precise caret placement.
  private def literalPos(
      c: blackbox.Context
  )(tree: c.universe.Tree): Option[c.universe.Position] = {
    import c.universe._
    tree match {
      case Literal(Constant(_: String)) =>
        val p      = tree.pos
        val chars  = p.source.content
        val offset =
          if (
            p.point + 2 < chars.length &&
            chars(p.point) == '"' && chars(p.point + 1) == '"' && chars(p.point + 2) == '"'
          )
            3
          else 1
        Some(p.withPoint(p.point + offset))

      case Select(t, TermName("stripMargin") | TermName("trim")) => literalPos(c)(t)
      case Apply(Select(t, TermName("stripMargin") | TermName("trim")), _) => literalPos(c)(t)
      case Apply(_, List(inner)) => literalPos(c)(inner)
      case _ => None
    }
  }
}
