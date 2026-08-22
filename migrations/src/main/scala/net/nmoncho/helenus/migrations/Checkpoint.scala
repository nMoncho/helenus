/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

import com.datastax.oss.driver.api.core.CqlSession

/** Records which token ranges a migration has finished, so a restart can skip
  * them. A range is an idempotent unit of work for an upsert load, so re-running a
  * migration with a populated checkpoint resumes rather than starting over.
  *
  * Restart safety depends on the write being idempotent: plain `INSERT`/`UPDATE`
  * upserts are idempotent by primary key, so replaying a range writes the same rows
  * again harmlessly. Counter updates and deletes are not idempotent and need
  * explicit handling before relying on restart.
  *
  * Implementations must be safe to call concurrently: the executor reads several
  * ranges at once. [[Checkpoint.cassandra]] is durable across processes (and
  * cluster-wide); [[Checkpoint.file]] is durable on one host; [[Checkpoint.none]]
  * and [[Checkpoint.inMemory]] do not survive a process restart.
  */
trait Checkpoint {
  def isCompleted(split: RangeSplit): Boolean
  def markCompleted(split: RangeSplit): Unit
}

object Checkpoint {

  /** A stable key for a split, derived from its token bounds. */
  def key(split: RangeSplit): String = s"${split.start}:${split.end}"

  /** Records nothing: every range is read on every run. */
  val none: Checkpoint = new Checkpoint {
    def isCompleted(split: RangeSplit): Boolean = false
    def markCompleted(split: RangeSplit): Unit  = ()
  }

  /** In-memory checkpoint, for tests and for resuming within a single run. */
  def inMemory(completed: Iterable[RangeSplit] = Nil): Checkpoint = {
    val checkpoint = new InMemoryCheckpoint
    completed.foreach(checkpoint.markCompleted)
    checkpoint
  }

  /** File-backed checkpoint: appends each completed key to `path`, and on
    * construction reloads any keys already there, so a restart skips them.
    */
  def file(path: Path): Checkpoint = new FileCheckpoint(path)

  /** Durable checkpoint backed by a Cassandra table (default
    * `helenus_migration_progress`) in the session's keyspace, keyed by
    * `(migration, range)`. The table is created if absent, and the ranges already
    * completed for `migration` are loaded on construction, so a restart of the same
    * named migration skips them. Recording a completion is a single idempotent
    * upsert, so concurrent marks are safe.
    */
  def cassandra(
      session: CqlSession,
      migration: String,
      table: String = "helenus_migration_progress"
  ): Checkpoint = new CassandraCheckpoint(session, migration, table)

  private final class InMemoryCheckpoint extends Checkpoint {
    private val done = ConcurrentHashMap.newKeySet[String]()

    def isCompleted(split: RangeSplit): Boolean = done.contains(key(split))
    def markCompleted(split: RangeSplit): Unit  = { val _ = done.add(key(split)) }
  }

  private final class FileCheckpoint(path: Path) extends Checkpoint {
    import scala.jdk.CollectionConverters._

    private val done = ConcurrentHashMap.newKeySet[String]()

    if (Files.exists(path)) {
      Files.readAllLines(path, UTF_8).asScala.foreach { line =>
        if (line.nonEmpty) { val _ = done.add(line) }
      }
    }

    def isCompleted(split: RangeSplit): Boolean = done.contains(key(split))

    def markCompleted(split: RangeSplit): Unit = synchronized {
      val entry = key(split)
      if (done.add(entry)) {
        val _ = Files.write(
          path,
          (entry + System.lineSeparator).getBytes(UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND
        )
      }
    }
  }

  private final class CassandraCheckpoint(session: CqlSession, migration: String, table: String)
      extends Checkpoint {
    import scala.jdk.CollectionConverters._

    private val done = ConcurrentHashMap.newKeySet[String]()

    session.execute(
      s"CREATE TABLE IF NOT EXISTS $table " +
        "(migration text, range_key text, PRIMARY KEY (migration, range_key))"
    )

    private val insert = session.prepare(s"INSERT INTO $table (migration, range_key) VALUES (?, ?)")
    private val select = session.prepare(s"SELECT range_key FROM $table WHERE migration = ?")

    session.execute(select.bind(migration)).iterator().asScala.foreach { row =>
      val _ = done.add(row.getString("range_key"))
    }

    def isCompleted(split: RangeSplit): Boolean = done.contains(key(split))

    def markCompleted(split: RangeSplit): Unit = {
      val entry = key(split)
      if (done.add(entry)) {
        val _ = session.execute(insert.bind(migration, entry))
      }
    }
  }
}
