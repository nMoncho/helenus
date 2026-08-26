/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.monix

import scala.concurrent.duration._

import _root_.monix.execution.Scheduler.Implicits.global
import _root_.monix.reactive.Observable
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus._
import net.nmoncho.helenus.migrations.Checkpoint
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.RateLimit
import net.nmoncho.helenus.migrations.RingPlan
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B7 for Monix: the token-range executor reads a whole table concurrently, sets a
  * routing token per range, honors the checkpoint, and reports metrics. Streams are
  * run against the embedded session with Monix's global scheduler.
  */
class TokenRangeMonixReadSpec extends AnyWordSpec with Matchers with CassandraSpec {

  private implicit def cql: CqlSession = session

  private val table = "monix_token_read"
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

  private val selectByRange =
    s"SELECT id FROM $table WHERE token(id) > ? AND token(id) <= ?"

  private def statement =
    selectByRange.toUnsafeCQL.prepare[Token, Token].as[Row]

  "asTokenRangeObservable (Monix)" should {

    "read every row exactly once across concurrent ranges, uncapped by default" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)
      val ids = collect(statement.asTokenRangeObservable(plan, parallelism = 4)).map(_.getInt("id"))

      ids.size shouldBe total // no range read a row twice
      ids.toSet shouldBe (1 to total).toSet // every row covered, seam included
    }

    "set the routing token to each range start (token-aware routing)" in {
      val plan  = TokenRangePlanner.plan(splitsPerRange = 4)
      val pstmt = statement

      plan.splits should not be empty
      plan.splits.foreach { split =>
        pstmt.tokenRangeStatement(split).getRoutingToken shouldBe split.start
      }
    }

    "apply an opt-in rate limit when one is configured" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)
      val ids  = collect(
        statement.asTokenRangeObservable(
          plan,
          parallelism = 4,
          rateLimit   = Some(RateLimit(20, 1.second))
        )
      ).map(_.getInt("id"))

      ids.toSet shouldBe (1 to total).toSet
    }

    "skip ranges already recorded in the checkpoint" in {
      val plan       = TokenRangePlanner.plan(splitsPerRange = 8)
      val checkpoint = Checkpoint.inMemory()
      plan.splits.take(plan.size / 2).foreach(checkpoint.markCompleted)

      val rows =
        collect(statement.asTokenRangeObservable(plan, parallelism = 4, checkpoint = checkpoint))

      rows.size should be < total
      plan.splits.forall(checkpoint.isCompleted) shouldBe true
    }

    "report extracted rows and range completions through metrics" in {
      val plan    = TokenRangePlanner.plan(splitsPerRange = 8)
      val metrics = MigrationMetrics.counting()

      val _ = collect(statement.asTokenRangeObservable(plan, parallelism = 4, metrics = metrics))

      metrics.extracted shouldBe total.toLong
      metrics.rangesCompleted shouldBe plan.size.toLong
      metrics.fraction shouldBe 1.0d +- 0.0001d
    }

    "return an empty observable for an empty plan" in {
      collect(statement.asTokenRangeObservable(RingPlan(Vector.empty))) shouldBe empty
    }
  }

  private def collect[A](observable: Observable[A]): List[A] =
    observable.toListL.runSyncUnsafe(30.seconds)
}
