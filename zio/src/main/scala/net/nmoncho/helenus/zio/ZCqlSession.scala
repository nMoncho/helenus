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
import com.datastax.oss.driver.api.core.servererrors.SyntaxError
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.RowMapper
import zio._

object ZCqlSession {

  /** Creates and opens a [[ZCqlSession]] against a Cassandra Ring
    *
    * This method pickups the configuration from a Typesafe Config, as the
    * underlying Datastax/Apache Java Driver uses that for configuration.
    *
    * Custom configuration can be done by extending the configuration defined in
    * the path [[DefaultDriverConfigLoader.DEFAULT_ROOT_PATH]], and then using
    * `configPath` to point to that extension.
    *
    * @param configPath what Typesafe Config path to use for configuring the session, defaults to [[DefaultDriverConfigLoader.DEFAULT_ROOT_PATH]]
    * @return Scoped [[ZCqlSession]] instance
    */
  def open(
      configPath: String = DefaultDriverConfigLoader.DEFAULT_ROOT_PATH
  ): ZIO[Scope, CassandraException, ZCqlSession] =
    open(
      ZIO
        .attempt(ConfigFactory.load())
        .flatMap(config => ZDefaultCqlSession(config, configPath))
    )

  /** Creates and opens a [[ZCqlSession]] against a Cassandra Ring
    *
    * Unlike the other `open` method, this one allows you to define how to create the Cassandra session.
    *
    * See the available `apply` methods on [[ZCqlSession]] companion object.
    *
    * @param sessionTask tasks creates
    * @return Scoped [[ZCqlSession]] instance
    */
  def open(
      sessionTask: Task[ZCqlSession]
  ): ZIO[Scope, CassandraException, ZCqlSession] =
    (for {
      _ <- ZIO.logInfo("Opening Cassandra Session...")
      session <- sessionTask
      _ <- ZIO.logInfo("Cassandra session is ready")
    } yield session)
      .mapError(new SessionOpenException("Failed to open Cassandra session", _))
      .withFinalizer(close)

  /** Closes a Cassandra Session.
    *
    * To be used as finalizer
    *
    * @param session session to close
    */
  private def close(session: ZCqlSession): ZIO[Any, Nothing, Unit] =
    (for {
      _ <- ZIO.logInfo("Closing Cassandra session")
      _ <- session.close()
      _ <- ZIO.logInfo("Cassandra session closed")
    } yield ())
      // TODO add stack trace to logging
      .catchAll(t => ZIO.logError("Failed trying to close cassandra session:\n" + t.getMessage))
}

object ZDefaultCqlSession {

  /** Simple Cassandra Session creation method
    *
    * @param host host of one contact point
    * @param port port of one contact point
    * @param datacenter datatcenter of one contact point, defaults to "datacenter1"
    */
  def apply(host: String, port: Int, datacenter: String = "datacenter1"): Task[ZDefaultCqlSession] =
    apply(
      CqlSession
        .builder()
        .addContactPoint(new InetSocketAddress(host, port))
        .withLocalDatacenter(datacenter)
    )

  /** Cassandra Session creation method using reference configuration
    *
    * For more information see https://docs.datastax.com/en/developer/java-driver/latest/manual/core/configuration/reference/index.html
    *
    * @param config typesafe Config with Cassandra configuration
    * @param configPath path to configuration
    */
  def apply(config: com.typesafe.config.Config, configPath: String): Task[ZDefaultCqlSession] =
    apply(
      new CqlSessionBuilder()
        .withConfigLoader(new DefaultDriverConfigLoader(() => config.getConfig(configPath)))
    )

  /** Cassandra Session creation method using a [[CqlSessionBuilder]]
    *
    * @param builder builder with desired configuration
    */
  def apply(builder: => CqlSessionBuilder): Task[ZDefaultCqlSession] =
    ZIO.fromCompletionStage(builder.buildAsync()).map(new ZDefaultCqlSession(_))
}

trait ZCqlSession {

  /** Closes this session
    */
  def close(): IO[CassandraException, Unit]

  /** Prepares a query synchronously
    *
    * @param query query to prepare
    * @return [[PreparedStatement]]
    */
  def prepare(query: String): IO[CassandraException, PreparedStatement]

  /** Prepares a query asynchronously
    *
    * @param query query to prepare
    * @return [[PreparedStatement]]
    */
  def prepareAsync(query: String): IO[CassandraException, PreparedStatement]

  /** Executes a simple (unprepared) statement synchronously
    *
    * @param statement statement to executed
    * @return [[ResultSet]] from execution
    */
  def execute(statement: String): IO[CassandraException, ResultSet]

  /** Executes a [[ScalaBoundStatement]] synchronously
    *
    * @param stmt statement to execute
    * @tparam Out result type of every row
    * @return [[PagingIterable]] containing rows
    */
  def execute[Out: RowMapper](
      stmt: ScalaBoundStatement[Out]
  ): IO[CassandraException, PagingIterable[Try[Out]]]

  /** Executes a simple (unprepared) statement asynchronously
    *
    * @param statement statement to executed
    * @return [[AsyncResultSet]] from execution
    */
  def executeAsync(statement: String): IO[CassandraException, AsyncResultSet]

  /** Executes a [[ScalaBoundStatement]] asynchronously
    *
    * @param stmt statement to execute
    * @tparam Out result type of every row
    * @return [[MappedAsyncPagingIterable]] containing rows
    */
  def executeAsync[Out: RowMapper](
      stmt: ScalaBoundStatement[Out]
  ): IO[CassandraException, MappedAsyncPagingIterable[Try[Out]]]

  /** Executes a [[BoundStatement]] asynchronously
    *
    * @param bs statement to execute
    * @return [[AsyncResultSet]] from execution
    */
  def executeAsyncFromJava(bs: BoundStatement): IO[CassandraException, AsyncResultSet]
}

class ZDefaultCqlSession(private val session: CqlSession) extends ZCqlSession {

  override def close(): IO[CassandraException, Unit] =
    ZIO
      .fromCompletionStage(session.closeAsync())
      .map(_ => ())
      .mapError(
        new SessionClosingException("Something went wrong while closing the session", _)
      )

  override def prepare(query: String): IO[CassandraException, PreparedStatement] =
    ZIO
      .attempt(session.prepare(query))
      .mapError {
        case syntaxError: SyntaxError =>
          new InvalidStatementException(s"Invalid query syntax [$query]", syntaxError)

        case npe: NullPointerException if query == null =>
          new InvalidStatementException("Query cannot be null", npe)

        case ex =>
          new GenericCassandraException(s"Something went wrong while preparing query [$query]", ex)
      }

  override def prepareAsync(query: String): IO[CassandraException, PreparedStatement] =
    ZIO
      .fromCompletionStage(session.prepareAsync(query))
      .mapError {
        case syntaxError: SyntaxError =>
          new InvalidStatementException(s"Invalid query syntax [$query]", syntaxError)

        case npe: NullPointerException if query == null =>
          new InvalidStatementException("Query cannot be null", npe)

        case ex =>
          new GenericCassandraException(s"Something went wrong while preparing query [$query]", ex)
      }

  override def execute(statement: String): IO[CassandraException, ResultSet] =
    ZIO
      .attempt(session.execute(statement))
      .mapError(ex =>
        new StatementExecutionException(
          "Something went wrong while trying to execute a statement",
          ex
        )
      )

  override def execute[Out: RowMapper](
      statement: ScalaBoundStatement[Out]
  ): IO[CassandraException, PagingIterable[Try[Out]]] = {
    val mapper = implicitly[RowMapper[Out]]

    ZIO
      .attempt(session.execute(statement))
      .map(rs => rs.map[Try[Out]]((row: Row) => Try(mapper(row))))
      .mapError(ex =>
        new StatementExecutionException(
          "Something went wrong while trying to execute a statement",
          ex
        )
      )
  }

  override def executeAsync(statement: String): IO[CassandraException, AsyncResultSet] =
    ZIO
      .fromCompletionStage(session.executeAsync(statement))
      .mapError(ex =>
        new StatementExecutionException(
          "Something went wrong while trying to execute async a statement",
          ex
        )
      )

  override def executeAsync[Out: RowMapper](
      statement: ScalaBoundStatement[Out]
  ): IO[CassandraException, MappedAsyncPagingIterable[Try[Out]]] = {
    val mapper = implicitly[RowMapper[Out]]

    ZIO
      .fromCompletionStage(session.executeAsync(statement))
      .map(rs => rs.map[Try[Out]]((row: Row) => Try(mapper(row))))
      .mapError(ex =>
        new StatementExecutionException(
          "Something went wrong while trying to execute async a statement",
          ex
        )
      )
  }

  override def executeAsyncFromJava(bs: BoundStatement): IO[CassandraException, AsyncResultSet] =
    ZIO
      .fromCompletionStage(session.executeAsync(bs))
      .mapError(ex =>
        new StatementExecutionException(
          "Something went wrong while trying to execute a statement",
          ex
        )
      )
}
