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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.util.Strings
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement

object CqlQueryInterpolation {

  def cql(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(session: c.Expr[CqlSession]): c.Expr[ScalaBoundStatement[Row]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

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

    c.Expr[ScalaBoundStatement[Row]](
      q"$expr.asInstanceOf[_root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement[_root_.com.datastax.oss.driver.api.core.cql.Row]]"
    )
  }

  def asyncCql(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[CqlSession],
      ec: c.Expr[ExecutionContext]
  ): c.Expr[Future[ScalaBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    val pstmt = c.Expr[Future[PreparedStatement]](
      q"_root_.scala.jdk.javaapi.FutureConverters.asScala($session.prepareAsync($stmt))"
    )

    val bounders = bindParameters.map { parameter =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[Unit](
          q"bstmt = bstmt.set(${parameter.tree.symbol.name.toString}, $parameter, $codec)"
        )
      }
    }

    val expr = c.Expr[Future[ScalaBoundStatement[Row]]](
      q"$pstmt.map { stmt => var bstmt = stmt.bind(); ..$bounders; bstmt.asInstanceOf[_root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement[_root_.com.datastax.oss.driver.api.core.cql.Row]]}($ec)"
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
          val named =
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
}
