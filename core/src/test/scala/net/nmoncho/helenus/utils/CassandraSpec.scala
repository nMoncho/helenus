/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.utils

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.SimpleStatement
import com.datastax.oss.driver.api.core.cql.Statement
import com.datastax.oss.driver.api.core.servererrors.AlreadyExistsException
import net.nmoncho.helenus.utils.CassandraSpec.ddlLock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite

trait CassandraSpec extends BeforeAndAfterAll with BeforeAndAfterEach { this: Suite =>

  protected lazy val keyspace: String = randomIdentifier("tests")

  private val hostname = "localhost"
  private val port     = 9142

  protected val contactPoint: String = s"$hostname:$port"

  protected lazy val session: CqlSession = CqlSession
    .builder()
    .addContactPoint(new InetSocketAddress(hostname, port))
    .withLocalDatacenter("datacenter1")
    .build()

  override def afterEach(): Unit = {
    super.afterEach()
    truncateKeyspace(keyspace)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    session.execute(
      s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1}"
    )
    session.execute(s"USE $keyspace")
  }

  override def afterAll(): Unit = {
    session.close()
    super.afterAll()
  }

  def randomIdentifier(prefix: String): String =
    s"${prefix}_${UUID.randomUUID().toString}".replaceAll("-", "_")

  def execute(query: String): ResultSet =
    session.execute(query)

  /** Executes a DDL statement until success.
    *
    * This is a workaround for the exception `DriverTimeoutException: Query timed out after PT2S`
    */
  def executeDDL(ddl: String): Unit = {
    ddlLock.lock()

    try {
      session.execute(SimpleStatement.newInstance(ddl).setConsistencyLevel(ConsistencyLevel.ALL))
    } catch {
      case _: AlreadyExistsException => // ignore
      case _: Throwable =>
        Thread.sleep(50)
        executeDDL(ddl)
    } finally {
      ddlLock.unlock()
    }
  }

  def execute(stmt: Statement[_]): ResultSet =
    session.execute(stmt)

  def executeFile(filename: String): Unit =
    scala.util.Using(scala.io.Source.fromResource(filename)) { src =>
      val body       = src.getLines().mkString("\n")
      val statements = body.split(";")

      statements.foreach(executeDDL)
    }

  def registerCodec[T](codec: TypeCodec[T]): Unit =
    session.getContext.getCodecRegistry.asInstanceOf[MutableCodecRegistry].register(codec)

  def truncateKeyspace(keyspace: String): Unit = {
    import scala.jdk.CollectionConverters._

    val rs = session
      .execute(s"SELECT table_name FROM system_schema.tables WHERE keyspace_name = '$keyspace'")
      .asScala

    rs.foreach(row => session.execute(s"TRUNCATE TABLE ${keyspace}.${row.getString(0)}"))
  }
}

object CassandraSpec {
  // using a Lock to avoid executing DDL concurrently on concurrent tests
  private val ddlLock: ReentrantLock = new ReentrantLock()
}
