/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.pekko

import scala.concurrent.Future

import _root_.net.nmoncho.helenus._
import _root_.net.nmoncho.helenus.pekko._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/** H2: a full Cassandra to Cassandra ETL end to end (extract via the token-range
  * executor, transform, load via the Helenus write sink).
  */
class TokenRangeMigrationSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaFutures {

  import TokenRangeMigrationSpec.Migrated

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("migrations-e2e-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val source = "h2_source"
  private val target = "h2_target"
  private val total  = 150

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(s"CREATE TABLE IF NOT EXISTS $source (id int PRIMARY KEY, v text)")
    executeDDL(s"CREATE TABLE IF NOT EXISTS $target (id int PRIMARY KEY, v text)")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    (1 to total).foreach(i => execute(s"INSERT INTO $source (id, v) VALUES ($i, 'v$i')"))
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  "A Cassandra to Cassandra migration" should {

    "extract, transform, and load every row (parallelism > 1)" in withSession { implicit cql =>
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      val extract =
        s"SELECT id, v FROM $source WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
          .prepare[Token, Token]
          .as[Row]
          .asTokenRangeReadSource(plan, parallelism = 4)

      val load: Sink[Migrated, Future[Done]] =
        s"INSERT INTO $target (id, v) VALUES (?, ?)".toUnsafeCQL
          .prepare[Int, String]
          .from[Migrated]
          .asWriteSink(CassandraWriteSettings.defaults)

      val migration = extract
        .map(row => Migrated(row.getInt("id"), row.getString("v").toUpperCase))
        .runWith(load)

      whenReady(migration) { _ =>
        val migrated = cql.execute(s"SELECT id, v FROM $target").as[Migrated].iter.toList

        migrated.map(m => m.id -> m.v).toMap shouldBe (1 to total).map(i => i -> s"V$i").toMap
      }
    }

    "run as one composed migration via asTokenRangeMigration, reporting metrics" in
    withSession { implicit cql =>
      val plan    = TokenRangePlanner.plan(splitsPerRange = 8)
      val metrics = MigrationMetrics.counting()

      val load: Sink[Migrated, Future[Done]] =
        s"INSERT INTO $target (id, v) VALUES (?, ?)".toUnsafeCQL
          .prepare[Int, String]
          .from[Migrated]
          .asWriteSink(CassandraWriteSettings.defaults)

      val migration =
        s"SELECT id, v FROM $source WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
          .prepare[Token, Token]
          .as[Row]
          .asTokenRangeMigration(
            plan,
            transform =
              Flow[Row].map(row => Migrated(row.getInt("id"), row.getString("v").toUpperCase)),
            sink        = load,
            parallelism = 4,
            metrics     = metrics
          )

      whenReady(migration.run()) { _ =>
        val migrated = cql.execute(s"SELECT id, v FROM $target").as[Migrated].iter.toList

        migrated.map(m => m.id -> m.v).toMap shouldBe (1 to total).map(i => i -> s"V$i").toMap
        metrics.extracted shouldBe total.toLong
        metrics.loaded shouldBe total.toLong
        metrics.rangesCompleted shouldBe plan.splits.size.toLong
      }
    }

    "write nothing in dry-run while still reporting counts (E4)" in withSession { implicit cql =>
      val plan    = TokenRangePlanner.plan(splitsPerRange = 8)
      val metrics = MigrationMetrics.counting()

      val load: Sink[Migrated, Future[Done]] =
        s"INSERT INTO $target (id, v) VALUES (?, ?)".toUnsafeCQL
          .prepare[Int, String]
          .from[Migrated]
          .asWriteSink(CassandraWriteSettings.defaults)

      val migration =
        s"SELECT id, v FROM $source WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
          .prepare[Token, Token]
          .as[Row]
          .asTokenRangeMigration(
            plan,
            transform = Flow[Row].map(row => Migrated(row.getInt("id"), row.getString("v"))),
            sink      = load,
            dryRun    = true,
            metrics   = metrics
          )

      whenReady(migration.run()) { _ =>
        cql.execute(s"SELECT COUNT(*) FROM $target").one().getLong(0) shouldBe 0L // wrote nothing
        metrics.extracted shouldBe total.toLong // but read all
        metrics.loaded shouldBe total.toLong // and transformed all
      }
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

object TokenRangeMigrationSpec {

  final case class Migrated(id: Int, v: String)

  object Migrated {
    implicit val rowMapper: RowMapper[Migrated]            = RowMapper[Migrated]()
    implicit val adapter: Adapter[Migrated, (Int, String)] = Adapter.builder[Migrated].build
  }
}
