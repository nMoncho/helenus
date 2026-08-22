/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.pekko

import java.util.concurrent.atomic.AtomicInteger

import _root_.net.nmoncho.helenus._
import _root_.net.nmoncho.helenus.pekko._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.migrations.Checkpoint
import net.nmoncho.helenus.migrations.RingPlan
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/** An interrupted migration restarts from a durable checkpoint, finishing the
  * remaining ranges without redoing the completed ones.
  */
class TokenRangeRestartSpec extends AnyWordSpec with Matchers with CassandraSpec with ScalaFutures {

  import TokenRangeRestartSpec.Migrated

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("migrations-restart-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val source        = "e3_source"
  private val target        = "e3_target"
  private val migrationName  = "e3-restart"
  private val total          = 200

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(s"CREATE TABLE IF NOT EXISTS $source (id int PRIMARY KEY, v text)")
    executeDDL(s"CREATE TABLE IF NOT EXISTS $target (id int PRIMARY KEY, v text)")
    // Pre-create the checkpoint table with the robust DDL path; the checkpoint's own
    // CREATE TABLE IF NOT EXISTS is then a no-op.
    executeDDL(
      "CREATE TABLE IF NOT EXISTS helenus_migration_progress " +
        "(migration text, range_key text, PRIMARY KEY (migration, range_key))"
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    (1 to total).foreach(i => execute(s"INSERT INTO $source (id, v) VALUES ($i, 'v$i')"))
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  "A migration resumed from a durable checkpoint" should {

    "finish the remaining ranges without redoing the completed ones" in withSession {
      implicit cql =>
        val plan          = TokenRangePlanner.plan(splitsPerRange = 8)
        val (firstHalf, _) = plan.splits.splitAt(plan.splits.size / 2)

        // Phase 1: an interrupted run that only got through the first half of the ring.
        val checkpoint1  = Checkpoint.cassandra(cql, migrationName)
        val readInPhase1 = migrate(RingPlan(firstHalf), checkpoint1)
        val afterPhase1  = targetCount(cql)

        afterPhase1 should be > 0     // it made real progress
        afterPhase1 should be < total // but did not finish
        readInPhase1 shouldBe afterPhase1

        // Phase 2: a restart. A fresh checkpoint reloads the completed ranges from the
        // table, so the executor skips them.
        val checkpoint2  = Checkpoint.cassandra(cql, migrationName)
        val readInPhase2 = migrate(plan, checkpoint2)

        targetCount(cql) shouldBe total                 // the migration is now complete
        readInPhase2 shouldBe (total - afterPhase1)     // phase 2 did not redo the first half
    }
  }

  /** Runs one migration pass and returns how many rows it read (== rows written). */
  private def migrate(plan: RingPlan, checkpoint: Checkpoint)(implicit cql: CqlSession): Int = {
    val read = new AtomicInteger(0)

    val extract =
      s"SELECT id, v FROM $source WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
        .prepare[Token, Token]
        .as[Row]
        .asTokenRangeReadSource(plan, parallelism = 4, checkpoint = checkpoint)
        .map { row =>
          read.incrementAndGet()
          Migrated(row.getInt("id"), row.getString("v"))
        }

    val load =
      s"INSERT INTO $target (id, v) VALUES (?, ?)".toUnsafeCQL
        .prepare[Int, String]
        .from[Migrated]
        .asWriteSink(CassandraWriteSettings.defaults)

    whenReady(extract.runWith(load))(_ => ())
    read.get()
  }

  private def targetCount(cql: CqlSession): Int =
    cql.execute(s"SELECT COUNT(*) FROM $target").one().getLong(0).toInt

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

object TokenRangeRestartSpec {

  final case class Migrated(id: Int, v: String)

  object Migrated {
    implicit val rowMapper: RowMapper[Migrated]            = RowMapper[Migrated]()
    implicit val adapter: Adapter[Migrated, (Int, String)] = Adapter.builder[Migrated].build
  }
}
