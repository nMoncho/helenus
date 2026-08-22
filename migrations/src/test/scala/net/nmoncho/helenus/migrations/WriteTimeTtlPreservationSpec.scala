/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus._
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A migration preserves WRITETIME and TTL by reading them from the source and
  * writing the target with `USING TIMESTAMP ? AND TTL ?`.
  */
class WriteTimeTtlPreservationSpec extends AnyWordSpec with Matchers with CassandraSpec {

  private implicit def implicitSession: CqlSession = session

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("CREATE TABLE IF NOT EXISTS src (id int PRIMARY KEY, v text, w text)")
    executeDDL("CREATE TABLE IF NOT EXISTS tgt (id int PRIMARY KEY, v text, w text)")
  }

  "A migration that preserves WRITETIME and TTL" should {

    "keep the source write timestamp and a comparable TTL on the target" in {
      val oldTimestamp = (System.currentTimeMillis() - 10L * 24 * 3600 * 1000) * 1000 // micros
      val ttlSeconds   = 86400 // 1 day

      execute(
        s"INSERT INTO src (id, v, w) VALUES (1, 'a', 'bb') USING TIMESTAMP $oldTimestamp AND TTL $ttlSeconds"
      )

      val srcRow = execute(
        "SELECT WRITETIME(v) AS wt_v, WRITETIME(w) AS wt_w, TTL(v) AS ttl_v, TTL(w) AS ttl_w " +
          "FROM src WHERE id = 1"
      ).one()

      val meta = WriteTimeTtl.rowLevel(
        writetimes = Seq(srcRow.getLong("wt_v"), srcRow.getLong("wt_w")),
        ttls       = Seq(ttlColumn(srcRow, "ttl_v"), ttlColumn(srcRow, "ttl_w"))
      )

      // Write the target with the preserved timestamp and TTL, bound via Helenus.
      "INSERT INTO tgt (id, v, w) VALUES (?, ?, ?) USING TIMESTAMP ? AND TTL ?".toUnsafeCQL
        .prepare[Int, String, String, Long, Int]
        .execute(1, "a", "bb", meta.timestamp, meta.ttlOrZero)

      val tgtRow = execute("SELECT WRITETIME(v) AS wt, TTL(v) AS ttl FROM tgt WHERE id = 1").one()

      tgtRow.getLong("wt") shouldBe oldTimestamp // exact write timestamp preserved

      val remaining = tgtRow.getInt("ttl")
      remaining should be <= ttlSeconds
      remaining should be > (ttlSeconds - 120) // within a comfortable tolerance
    }
  }

  private def ttlColumn(row: Row, name: String): Option[Int] =
    if (row.isNull(name)) None else Some(row.getInt(name))
}
