/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.pekko

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus._
import net.nmoncho.helenus.migrations.RingPlan
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class TokenRangePekkoReadSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(15, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("migrations-pekko-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val table = "b1_token_read"
  private val total = 200

  override def beforeAll(): Unit = {
    super.beforeAll()

    executeDDL(s"CREATE TABLE IF NOT EXISTS $table (id int PRIMARY KEY, v text)")
    (1 to total).foreach(i => execute(s"INSERT INTO $table (id, v) VALUES ($i, 'v$i')"))
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  "asTokenRangeReadSource (Pekko)" should {

    "read every row exactly once across concurrent ranges" in withSession { implicit cql =>
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      val source = s"SELECT id FROM $table WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
        .prepare[Token, Token]
        .as[Row]
        .asTokenRangeReadSource(plan, parallelism = 4)

      whenReady(source.runWith(Sink.seq)) { rows =>
        val ids = rows.map(_.getInt("id"))
        ids.size shouldBe total     // no range read a row twice
        ids.toSet.size shouldBe total // every row was covered, seam included
      }
    }

    "return an empty source for an empty plan" in withSession { implicit cql =>
      val source = s"SELECT id FROM $table WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
        .prepare[Token, Token]
        .as[Row]
        .asTokenRangeReadSource(RingPlan(Vector.empty))

      whenReady(source.runWith(Sink.seq))(_ shouldBe empty)
    }
  }

  private def withSession(fn: CqlSession => Unit): Unit = {
    import system.dispatcher
    whenReady(as.underlying().map(fn))(_ => ())
  }

  private def cassandraConfig: Config = ConfigFactory
    .parseString(s"""
                    |datastax-java-driver.basic {
                    |  contact-points = ["$contactPoint"]
                    |  session-keyspace = "$keyspace"
                    |  load-balancing-policy.local-datacenter = "datacenter1"
                    |}""".stripMargin)
    .withFallback(ConfigFactory.load())
}
