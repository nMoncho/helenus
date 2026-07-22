/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.integration

import java.net.InetSocketAddress

import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Base for the integration specs: everything the DSL generates is executed
  * against a real (embedded) Cassandra, so these tests verify that the CQL we
  * render, and the statements the execute gates admit, are genuinely valid.
  *
  * The specs are marked `@DoNotDiscover` and run sequentially through
  * [[IntegrationSuites]] because they share keyspaces and tables.
  */
trait CassandraIntegrationSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val hostname = "localhost"
  private val port     = 9142

  protected val contactPoint: String = s"$hostname:$port"

  protected implicit lazy val session: CqlSession = {
    val s = CqlSession
      .builder()
      .addContactPoint(new InetSocketAddress(hostname, port))
      .withLocalDatacenter("datacenter1")
      .build()

    // create keyspaces for all tests
    Seq("my_keyspace", "analytics", "iot", "monitoring", "blog").foreach { ks =>
      s.execute(
        s"CREATE KEYSPACE IF NOT EXISTS $ks WITH replication = " +
          "{'class': 'SimpleStrategy', 'replication_factor': 1}"
      )
    }

    s
  }

  protected def execute(cql: String): ResultSet = session.execute(cql)

  protected def rows(cql: String): List[Row] =
    session.execute(cql).all().asScala.toList

  protected def rows(rs: ResultSet): List[Row] =
    rs.all().asScala.toList
}
