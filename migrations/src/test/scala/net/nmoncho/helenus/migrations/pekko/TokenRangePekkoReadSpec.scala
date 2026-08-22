/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.pekko

import java.util.concurrent.atomic.AtomicBoolean

import scala.concurrent.duration._

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.DriverTimeoutException
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus._
import net.nmoncho.helenus.migrations.RateLimit
import net.nmoncho.helenus.migrations.RetryPolicy
import net.nmoncho.helenus.migrations.RingPlan
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.migrations.TokenRangeReadException
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
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

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("migrations-pekko-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val table = "b1_token_read"
  private val total = 100

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(s"CREATE TABLE IF NOT EXISTS $table (id int PRIMARY KEY, v text)")
  }

  // `CassandraSpec.afterEach` truncates the keyspace after every test, so re-seed
  // the table before each one.
  override def beforeEach(): Unit = {
    super.beforeEach()
    (1 to total).foreach(i => execute(s"INSERT INTO $table (id, v) VALUES ($i, 'v$i')"))
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  private val selectByRange =
    s"SELECT id FROM $table WHERE token(id) > ? AND token(id) <= ?"

  "asTokenRangeReadSource (Pekko)" should {

    "read every row exactly once across concurrent ranges, uncapped by default" in withSession {
      implicit cql =>
        val plan = TokenRangePlanner.plan(splitsPerRange = 8)

        val source = selectByRange.toUnsafeCQL
          .prepare[Token, Token]
          .as[Row]
          .asTokenRangeReadSource(plan, parallelism = 4)

        whenReady(source.runWith(Sink.seq)) { rows =>
          val ids = rows.map(_.getInt("id"))
          ids.size shouldBe total // no range read a row twice
          ids.toSet.size shouldBe total // every row was covered, seam included
        }
    }

    "set the routing token to each range start (token-aware routing)" in withSession {
      implicit cql =>
        val plan  = TokenRangePlanner.plan(splitsPerRange = 4)
        val pstmt = selectByRange.toUnsafeCQL.prepare[Token, Token].as[Row]

        plan.splits should not be empty
        plan.splits.foreach { split =>
          pstmt.tokenRangeStatement(split).getRoutingToken shouldBe split.start
        }
    }

    "apply an opt-in rate limit when one is configured" in withSession { implicit cql =>
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      // 20 rows/second over 100 rows takes at least ~4s (one burst of 20, then
      // 80 more at 20/s); throttle enforces a minimum time, so the lower-bound
      // assertion is deterministic rather than flaky.
      val source = selectByRange.toUnsafeCQL
        .prepare[Token, Token]
        .as[Row]
        .asTokenRangeReadSource(plan, parallelism = 4, rateLimit = Some(RateLimit(20, 1.second)))

      val start = System.nanoTime()
      whenReady(source.runWith(Sink.seq)) { rows =>
        val elapsedMs = (System.nanoTime() - start) / 1000000L
        rows.size shouldBe total
        elapsedMs should be >= 3000L
      }
    }

    "split a timed-out range and read the halves" in withSession { implicit cql =>
      val whole     = TokenRangePlanner.plan(splitsPerRange = 1).splits.head.range
      val timedOnce = new AtomicBoolean(false)

      // Times out the first read of the whole range, then succeeds on the halves.
      def read(range: TokenRange): Source[Int, NotUsed] =
        if (range == whole && timedOnce.compareAndSet(false, true)) {
          Source.failed(new DriverTimeoutException("simulated timeout"))
        } else {
          Source.single(1)
        }

      val source = readWithRetry(whole, depth = 0, RetryPolicy.Default)(read)

      whenReady(source.runWith(Sink.seq)) { emitted =>
        timedOnce.get() shouldBe true // the whole range did time out
        emitted should not be empty // and the halves were read afterwards
      }
    }

    "fail with a clear TokenRangeReadException when split retries are exhausted" in
    withSession { implicit cql =>
      val whole = TokenRangePlanner.plan(splitsPerRange = 1).splits.head.range

      val read: TokenRange => Source[Int, NotUsed] =
        _ => Source.failed(new DriverTimeoutException("always times out"))

      val source = readWithRetry(whole, depth = 0, RetryPolicy(maxSplits = 2))(read)

      whenReady(source.runWith(Sink.seq).failed) { error =>
        error shouldBe a[TokenRangeReadException]
      }
    }

    "return an empty source for an empty plan" in withSession { implicit cql =>
      val source = selectByRange.toUnsafeCQL
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
