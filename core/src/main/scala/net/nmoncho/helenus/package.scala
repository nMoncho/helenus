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

package net.nmoncho

import com.datastax.oss.driver.api.core.cql.{ AsyncResultSet, BoundStatement, ResultSet }
import com.datastax.oss.driver.api.core.{ CqlSession, PagingIterable }
import net.nmoncho.helenus.api.`type`.codec.{ CodecDerivation, RowMapper }
import net.nmoncho.helenus.internal._
import net.nmoncho.helenus.internal.cql.ParameterValue
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement.CQLQuery

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }

package object helenus extends CodecDerivation {

  trait CqlSessionExtension extends CqlSessionSyncExtension with CqlSessionAsyncExtension {}

  implicit class ClqSessionOps(private val cqlSession: CqlSession) extends AnyVal {
    def toScala: CqlSessionExtension = new CqlSessionExtension {
      override val session: CqlSession = cqlSession
    }
  }

  /** Creates a [[BoundStatement]] using String Interpolation.
    * There is also an asynchronous alternative, which is `asyncCql` instead of `cql`.
    *
    * This won't execute the bound statement yet, just set its arguments.
    *
    * {{{
    * import net.nmoncho.helenus._
    *
    * val id = UUID.fromString("...")
    * val bstmt = cql"SELECT * FROM some_table WHERE id = $id"
    * }}}
    */
  implicit class CqlStringInterpolation(val sc: StringContext) extends AnyVal {

    def cql(args: ParameterValue*)(implicit session: CqlSessionSyncExtension): BoundStatement = {
      val query = cqlQuery(args)

      val bstmt = session.session.prepare(query).bind()
      setParameters(bstmt, args)
    }

    def asyncCql(
        args: ParameterValue*
    )(implicit session: CqlSessionSyncExtension, ec: ExecutionContext): Future[BoundStatement] = {
      import net.nmoncho.helenus.internal.compat.FutureConverters._

      val query = cqlQuery(args)

      session.session.prepareAsync(query).asScala.map { pstmt =>
        setParameters(pstmt.bind(), args)
      }
    }

    private def setParameters(bstmt: BoundStatement, args: Seq[ParameterValue]): BoundStatement =
      args
        .foldLeft(bstmt -> 0) { case ((bstmt, index), arg) =>
          arg.set(bstmt, index) -> (index + 1)
        }
        ._1

    private def cqlQuery(args: Seq[ParameterValue]): String = {
      val partsIt = sc.parts.iterator
      val argsIt  = args.iterator

      val sb = new mutable.StringBuilder(partsIt.next())
      while (argsIt.hasNext) {
        sb.append(argsIt.next().toCQL)
          .append(partsIt.next())
      }

      sb.toString()
    }

  }

  /** Extension methods for [[BoundStatement]], helping you execute them with the proper context.
    */
  implicit class BoundStatementSyncOps(private val bstmt: BoundStatement) extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    def execute()(implicit session: CqlSessionSyncExtension): ResultSet =
      session.session.execute(bstmt)

    def executeAsync()(implicit session: CqlSessionAsyncExtension): Future[AsyncResultSet] =
      session.session.executeAsync(bstmt).asScala
  }

  implicit class PreparedStatementSyncStringOps(private val query: String) extends AnyVal {

    def toCQL(implicit session: CqlSessionExtension): CQLQuery =
      CQLQuery(
        query,
        session.session
      )
  }

  implicit class ResultSetOps(private val rs: ResultSet) extends AnyVal {
    def as[T](implicit mapper: RowMapper[T]): PagingIterable[T] = rs.map(mapper.apply)
  }

  /** Extension methods for [[PagingIterable]]
    *
    * Mostly how to transform this Cassandra iterable into a more Scala idiomatic structure.
    */
  implicit class PagingIterableOps[T](private val pi: PagingIterable[T]) extends AnyVal {
    import scala.collection.compat._

    /** First potential element of this iterable
      */
    def headOption: Option[T] = Option(pi.one())

    /** This [[PagingIterable]] as a Scala [[Iterator]]
      */
    def iter: Iterator[T] = {
      import scala.jdk.CollectionConverters._

      pi.iterator().asScala
    }

    /** This [[PagingIterable]] as a Scala Collection
      *
      * Example
      * {{{
      *   import scala.collection.compat._ // Only for Scala 2.12
      *
      *   pagingIterable.to(List)
      *   pagingIterable.to(Set)
      * }}}
      */
    def to[Col[_]](factory: Factory[T, Col[T]])(
        implicit cbf: BuildFrom[Nothing, T, Col[T]]
    ): Col[T] = iter.to(factory)
  }
}
