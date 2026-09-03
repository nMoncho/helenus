/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.zio

import _root_.zio.Runtime
import _root_.zio.Unsafe
import _root_.zio.ZEnvironment
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.migrations.Checkpoint
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.zio._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The token-range executor reads a whole table concurrently, honoring
  * the checkpoint and reporting metrics. The stream is a `ZStream` in the
  * `ZCqlSession` environment, so the test wraps the embedded session in a
  * `ZDefaultCqlSession` and runs each stream with the default ZIO runtime.
  */
class TokenRangeZioReadSpec extends AnyWordSpec with Matchers with CassandraSpec {

  import TokenRangeZioReadSpec.Scanned

  private implicit def cql: CqlSession = session

  private final val table = "zio_token_read"
  private val total       = 100

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(s"CREATE TABLE IF NOT EXISTS $table (id int PRIMARY KEY, v text)")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    (1 to total).foreach(i => execute(s"INSERT INTO $table (id, v) VALUES ($i, 'v$i')"))
  }

  private final val readStatement =
    s"SELECT id FROM $table WHERE token(id) > ? AND token(id) <= ?".toZCQL
      .prepare[Token, Token]
      .to[Scanned]

  "asTokenRangeStream (ZIO)" should {

    "read every row exactly once across concurrent ranges" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)
      val ids  = runAll(readStatement.asTokenRangeStream(plan, parallelism = 4)).map(_.id)

      ids.size shouldBe total
      ids.toSet shouldBe (1 to total).toSet
    }

    "skip ranges already recorded in the checkpoint" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      // Pre-mark half of the ranges as completed: those must not be re-read.
      val checkpoint = Checkpoint.inMemory()
      plan.splits.take(plan.size / 2).foreach(checkpoint.markCompleted)

      val rows =
        runAll(readStatement.asTokenRangeStream(plan, parallelism = 4, checkpoint = checkpoint))

      rows.size should be < total
      // Every range is now recorded, whether skipped or freshly read.
      plan.splits.forall(checkpoint.isCompleted) shouldBe true
    }

    "report extracted rows and range completions through metrics" in {
      val plan    = TokenRangePlanner.plan(splitsPerRange = 8)
      val metrics = MigrationMetrics.counting()

      val _ = runAll(readStatement.asTokenRangeStream(plan, parallelism = 4, metrics = metrics))

      metrics.extracted shouldBe total.toLong
      metrics.rangesCompleted shouldBe plan.size.toLong
      metrics.fraction shouldBe 1.0d +- 0.0001d
    }
  }

  /** Runs a token-range `ZStream` against the embedded session (wrapped as a
    * `ZCqlSession`) and flattens the emitted `Chunk` pages into a single list.
    */
  private def runAll[A](stream: ZCqlStream[A]): List[A] = {
    val zSession: ZCqlSession = new ZDefaultCqlSession(cql)

    val pages = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe
        .run(stream.runCollect.provideEnvironment(ZEnvironment(zSession)))
        .getOrThrow()
    }

    pages.flatMap(_.toList).toList
  }
}

object TokenRangeZioReadSpec {

  final case class Scanned(id: Int)

  object Scanned {
    implicit val rowMapper: RowMapper[Scanned] = new RowMapper[Scanned] {
      override def apply(row: Row): Scanned = Scanned(row.getInt("id"))
    }
  }
}
