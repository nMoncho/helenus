/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import net.nmoncho.helenus._
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The token-range read path binds `Token` bounds with the core token codec
  * (from `import net.nmoncho.helenus._`), so no hand-rolled codec is needed.
  */
class TokenCodecReadSpec extends AnyWordSpec with Matchers with CassandraSpec {

  private implicit def implicitSession: CqlSession = session

  private val table = "token_read"
  private val total = 50

  override def beforeAll(): Unit = {
    super.beforeAll()

    executeDDL(s"CREATE TABLE IF NOT EXISTS $keyspace.$table (id int PRIMARY KEY, v text)")
    (1 to total).foreach { i =>
      execute(s"INSERT INTO $keyspace.$table (id, v) VALUES ($i, 'v$i')")
    }
  }

  "A token-range read bound with the core token codec" should {

    "read every row exactly once with no user-defined codec" in {
      val plan = TokenRangePlanner.plan(splitsPerRange = 8)

      val ids = plan.splits.flatMap(scanIds)

      ids.size shouldBe total // no range read a row twice
      ids.distinct.size shouldBe total // every row was covered
    }
  }

  private def scanIds(split: RangeSplit): List[Int] = {
    val rows: List[Row] =
      if (endsAtRingMinimum(split.end)) {
        s"SELECT id FROM $keyspace.$table WHERE token(id) > ?".toUnsafeCQL
          .prepare[Token]
          .as[Row]
          .execute(split.start)
          .iter
          .toList
      } else {
        s"SELECT id FROM $keyspace.$table WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
          .prepare[Token, Token]
          .as[Row]
          .execute(split.start, split.end)
          .iter
          .toList
      }

    rows.map(_.getInt("id"))
  }

  /** The range ending at the ring's minimum token represents "up to the maximum
    * token", so its upper bound must be left open. Executors handle this seam.
    */
  private def endsAtRingMinimum(end: Token): Boolean = end match {
    case m: Murmur3Token => m.getValue == Long.MinValue
    case _ => false
  }
}
