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

package net.nmoncho.helenus.zio

import java.net.InetSocketAddress

import scala.util.Try

import com.datastax.oss.driver.api.core._
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.RowMapper
import zio.IO

class ZLazyCqlSession(hostname: String, port: Int) extends ZCqlSession {

  private lazy val session = CqlSession
    .builder()
    .addContactPoint(new InetSocketAddress(hostname, port))
    .withLocalDatacenter("datacenter1")
    .build()

  private lazy val underlying = new ZDefaultCqlSession(session)

  override def close(): IO[CassandraException, Unit] =
    underlying.close()

  override def prepare(query: String): IO[CassandraException, PreparedStatement] =
    underlying.prepare(query)

  override def prepareAsync(query: String): IO[CassandraException, PreparedStatement] =
    underlying.prepareAsync(query)

  override def execute(statement: String): IO[CassandraException, ResultSet] =
    underlying.execute(statement)

  override def execute[Out: RowMapper](
      statement: ScalaBoundStatement[Out]
  ): IO[CassandraException, PagingIterable[Try[Out]]] =
    underlying.execute(statement)

  override def executeAsync(statement: String): IO[CassandraException, AsyncResultSet] =
    underlying.executeAsync(statement)

  override def executeAsync[Out: RowMapper](
      stmt: ScalaBoundStatement[Out]
  ): IO[CassandraException, MappedAsyncPagingIterable[Try[Out]]] =
    underlying.executeAsync(stmt)

  override def executeAsyncFromJava(bs: BoundStatement): IO[CassandraException, AsyncResultSet] =
    underlying.executeAsyncFromJava(bs)

  object unsafe {
    def executeFile(filename: String): Unit =
      scala.util.Using(scala.io.Source.fromResource(filename)) { src =>
        val body       = src.getLines().mkString("\n")
        val statements = body.split(";")

        statements.foreach(session.execute)
        Thread.sleep(100) // FIXME
      }

    def executeDDL(ddl: String): ResultSet =
      session.execute(SimpleStatement.newInstance(ddl).setConsistencyLevel(ConsistencyLevel.ALL))

    def withSession(fn: CqlSession => Unit): Unit =
      fn(session)
  }

}
