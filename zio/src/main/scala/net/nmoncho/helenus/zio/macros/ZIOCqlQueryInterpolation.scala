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

package net.nmoncho.helenus.zio.macros

import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.util.Strings
import net.nmoncho.helenus.api.cql.WrappedBoundStatement
import net.nmoncho.helenus.zio.CassandraException
import net.nmoncho.helenus.zio.ZCqlSession
import zio.ZIO

object ZIOCqlQueryInterpolation {

  def zcql(
      c: blackbox.Context
  )(
      params: c.Expr[Any]*
  ): c.Expr[ZIO[ZCqlSession, CassandraException, WrappedBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bounders) = buildStatementAndBounders(c)(params)

    c.Expr[ZIO[ZCqlSession, CassandraException, WrappedBoundStatement[Row]]](
      q"""_root_.zio.ZIO.serviceWithZIO[_root_.net.nmoncho.helenus.zio.ZCqlSession](
         _.prepare($stmt).map { pstmt => var bstmt = pstmt.bind(); ..$bounders; new _root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement(bstmt)(_root_.net.nmoncho.helenus.api.RowMapper.identity) }
       ).mapError(new _root_.net.nmoncho.helenus.zio.StatementExecutionException("Something went wrong while trying to execute an interpolated statement", _))
       """
    )
  }

  def zcqlAsync(
      c: blackbox.Context
  )(
      params: c.Expr[Any]*
  ): c.Expr[ZIO[ZCqlSession, CassandraException, WrappedBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bounders) = buildStatementAndBounders(c)(params)

    c.Expr[ZIO[ZCqlSession, CassandraException, WrappedBoundStatement[Row]]](
      q"""_root_.zio.ZIO.serviceWithZIO[_root_.net.nmoncho.helenus.zio.ZCqlSession](
         _.prepareAsync($stmt).map { pstmt => var bstmt = pstmt.bind(); ..$bounders; new _root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement(bstmt)(_root_.net.nmoncho.helenus.api.RowMapper.identity) }
       ).mapError(new _root_.net.nmoncho.helenus.zio.StatementExecutionException("Something went wrong while trying to execute async an interpolated statement", _))
       """
    )
  }

  /** Builds a CQL Statement and it Bounders
    *
    * @param c Context
    * @param params String Interpolated parameters
    * @return CQL statement, with its bounders
    */
  private def buildStatementAndBounders(
      c: blackbox.Context
  )(params: Seq[c.Expr[Any]]): (String, Seq[c.Expr[Unit]]) = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    val bounders = bindParameters.map { parameter =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[Unit](
          q"bstmt = bstmt.set(${parameter.tree.symbol.name.toString}, $parameter, $codec)"
        )
      }
    }

    stmt -> bounders
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
