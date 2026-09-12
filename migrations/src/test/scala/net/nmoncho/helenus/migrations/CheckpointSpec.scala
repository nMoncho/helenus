/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.nio.file.Files

import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Covers every [[Checkpoint]] implementation: the no-op and in-memory ones, the
  * file-backed one (persist + reload from a temp file), and the Cassandra-backed one
  * (persist + reload from a table on the embedded node).
  */
class CheckpointSpec extends AnyWordSpec with Matchers with CassandraSpec {

  private def split(start: Long, end: Long): RangeSplit =
    RangeSplit(
      new Murmur3TokenRange(new Murmur3Token(start), new Murmur3Token(end)),
      None,
      BigDecimal(1)
    )

  private val splitA = split(0L, 100L)
  private val splitB = split(100L, 200L)

  "Checkpoint.None" should {
    "never record anything" in {
      Checkpoint.None.isCompleted(splitA) shouldBe false
      Checkpoint.None.markCompleted(splitA)
      Checkpoint.None.isCompleted(splitA) shouldBe false
    }
  }

  "Checkpoint.inMemory" should {
    "record marked ranges and preload the given ones" in {
      val cp = Checkpoint.inMemory()
      cp.isCompleted(splitA) shouldBe false
      cp.markCompleted(splitA)
      cp.isCompleted(splitA) shouldBe true
      cp.isCompleted(splitB) shouldBe false

      Checkpoint.inMemory(List(splitB)).isCompleted(splitB) shouldBe true
    }
  }

  "Checkpoint.file" should {
    "persist completed ranges and reload them on restart" in {
      val path = Files.createTempFile("helenus-checkpoint", ".txt")
      try {
        val cp = Checkpoint.file(path)
        cp.isCompleted(splitA) shouldBe false
        cp.markCompleted(splitA)
        cp.markCompleted(splitA) // idempotent: does not append a duplicate line
        cp.markCompleted(splitB)
        cp.isCompleted(splitA) shouldBe true

        // A fresh checkpoint over the same file reloads the recorded ranges.
        val restarted = Checkpoint.file(path)
        restarted.isCompleted(splitA) shouldBe true
        restarted.isCompleted(splitB) shouldBe true
      } finally {
        val _ = Files.deleteIfExists(path)
      }
    }
  }

  "Checkpoint.cassandra" should {
    "persist completed ranges in a table and reload them on restart" in {
      val cp = Checkpoint.cassandra(session, migration = "mig-1")
      cp.isCompleted(splitA) shouldBe false
      cp.markCompleted(splitA)
      cp.markCompleted(splitA) // idempotent
      cp.isCompleted(splitA) shouldBe true

      // A fresh checkpoint for the same migration reloads the table state.
      Checkpoint.cassandra(session, migration = "mig-1").isCompleted(splitA) shouldBe true

      // A different migration name starts empty.
      Checkpoint.cassandra(session, migration = "mig-2").isCompleted(splitA) shouldBe false
    }
  }
}
