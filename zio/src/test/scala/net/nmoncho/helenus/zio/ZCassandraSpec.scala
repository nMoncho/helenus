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

import java.util.UUID

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.test.ZIOSpec

abstract class ZCassandraSpec extends ZIOSpec[ZCqlSession] {

  protected lazy val keyspace: String = randomIdentifier("tests")

  private val hostname   = "localhost"
  private val port       = 9142
  private val underlying = new ZLazyCqlSession(hostname, port)

  protected val contactPoint: String = s"$hostname:$port"

  override val bootstrap: ZLayer[Any, Throwable, ZCqlSession] =
    ZLayer.scoped(ZIO.attempt(underlying))

  def execute(statement: String): ZIO[ZCqlSession, Throwable, ResultSet] =
    ZIO.service[ZCqlSession].flatMap(_.execute(statement))

  def randomIdentifier(prefix: String): String =
    s"${prefix}_${UUID.randomUUID().toString}".replaceAll("-", "_")

  def executeFile(filename: String): Unit =
    underlying.unsafe.executeFile(filename)

  def withSession(fn: CqlSession => Unit): Unit =
    underlying.unsafe.withSession(fn)

  protected def createKeyspace(): ZIO[ZCqlSession, Throwable, Unit] = for {
    _ <- execute(
      s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1}"
    )
    _ <- execute(s"USE $keyspace")
  } yield ()

  protected def executeDDL(ddl: String): Task[Unit] =
    ZIO.attempt(underlying.unsafe.executeDDL(ddl)).map(_ => ())
}
