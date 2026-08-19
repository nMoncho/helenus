/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Conformance corpus for the CQL grammar (PLAN_CQL item C1).
  *
  * A broad, data-driven list of '''valid''' CQL that [[CqlValidator]] must accept. It is the safety
  * net for grammar changes — in particular for re-importing a newer Cassandra grammar (see
  * `tools/antlr-import/`): if an import silently drops a construct, the corresponding entry here
  * turns red.
  *
  * The bias is deliberate: this file only asserts '''acceptance'''. Rejecting valid CQL is the
  * harmful failure mode (an unbypassable compile error on the `cql"..."` / `toCQL` path), so every
  * entry is something Cassandra accepts. Keep it easy to grow: add a string to the relevant group.
  */
class CqlConformanceSpec extends AnyFlatSpec with Matchers {

  behavior of "The CQL grammar"

  private def acceptsAll(group: String, queries: Seq[String]): Unit =
    it should s"accept $group" in
      queries.foreach { q =>
        withClue(s"$q\n  ") {
          CqlValidator.validate(q) shouldBe Right(())
        }
      }

  acceptsAll(
    "SELECT statements",
    Seq(
      "SELECT * FROM t",
      "SELECT id, name FROM t",
      "SELECT ks.t.id FROM ks.t",
      "SELECT id AS pk FROM t",
      "SELECT DISTINCT id FROM t",
      "SELECT COUNT(*) FROM t",
      "SELECT JSON id FROM t",
      "SELECT id FROM t WHERE id = ? AND name = :name",
      "SELECT id FROM t WHERE id IN (1, 2, 3)",
      "SELECT id FROM t WHERE token(id) > token(?)",
      "SELECT id FROM t WHERE name CONTAINS 'x'",
      "SELECT id FROM t WHERE tags CONTAINS KEY 'k'",
      "SELECT id FROM t WHERE name LIKE 'a%'",
      "SELECT WRITETIME(name), TTL(name) FROM t",
      "SELECT MAXWRITETIME(name) FROM t",
      "SELECT CAST(count AS INT) FROM t",
      "SELECT id FROM t GROUP BY id ORDER BY name DESC PER PARTITION LIMIT 1 LIMIT 10 ALLOW FILTERING",
      "SELECT id FROM t LIMIT ?"
    )
  )

  acceptsAll(
    "INSERT statements",
    Seq(
      "INSERT INTO t (id, name) VALUES (?, ?)",
      "INSERT INTO ks.t (id, name) VALUES (1, 'a')",
      "INSERT INTO t (id, name) VALUES (?, ?) IF NOT EXISTS",
      "INSERT INTO t (id, name) VALUES (?, ?) USING TTL 86400 AND TIMESTAMP 1000",
      "INSERT INTO t JSON '{\"id\": 1}'",
      "INSERT INTO t (id, tags) VALUES (1, {'a', 'b'})",
      "INSERT INTO t (id, m) VALUES (1, {'a': 1, 'b': 2})",
      "INSERT INTO t (id, l) VALUES (1, [1, 2, 3])"
    )
  )

  acceptsAll(
    "UPDATE statements",
    Seq(
      "UPDATE t SET name = ? WHERE id = ?",
      "UPDATE t USING TTL 400 SET name = ? WHERE id = ?",
      "UPDATE t SET counter = counter + 1 WHERE id = ?",
      "UPDATE t SET l = l + [1] WHERE id = ?",
      "UPDATE t SET m = m + {'a': 1} WHERE id = ?",
      "UPDATE t SET name = ? WHERE id = ? IF name = 'old'",
      "UPDATE t SET name = ? WHERE id = ? IF EXISTS"
    )
  )

  acceptsAll(
    "DELETE and TRUNCATE statements",
    Seq(
      "DELETE FROM t WHERE id = ?",
      "DELETE name FROM t WHERE id = ?",
      "DELETE m['k'] FROM t WHERE id = ?",
      "DELETE FROM t WHERE id = ? IF EXISTS",
      "DELETE FROM t USING TIMESTAMP 1000 WHERE id = ?",
      "TRUNCATE t",
      "TRUNCATE TABLE ks.t"
    )
  )

  acceptsAll(
    "BATCH statements",
    Seq(
      "BEGIN BATCH INSERT INTO t (id) VALUES (1); INSERT INTO t (id) VALUES (2); APPLY BATCH",
      "BEGIN UNLOGGED BATCH INSERT INTO t (id) VALUES (?) APPLY BATCH",
      "BEGIN COUNTER BATCH UPDATE t SET c = c + 1 WHERE id = ? APPLY BATCH",
      "BEGIN BATCH USING TIMESTAMP 1000 INSERT INTO t (id) VALUES (1) APPLY BATCH"
    )
  )

  acceptsAll(
    "CREATE TABLE with data types",
    Seq(
      "CREATE TABLE t (id INT PRIMARY KEY, name TEXT)",
      "CREATE TABLE IF NOT EXISTS ks.t (id UUID PRIMARY KEY)",
      "CREATE TABLE t (a INT, b INT, c INT, PRIMARY KEY ((a, b), c))",
      "CREATE TABLE t (id INT PRIMARY KEY, s SET<TEXT>, l LIST<INT>, m MAP<TEXT, INT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<LIST<INT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<LIST<INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup TUPLE<INT, TEXT, FLOAT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, v VECTOR<FLOAT, 3>)",
      "CREATE TABLE t (id INT PRIMARY KEY, s TEXT STATIC)",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH comment = 'x' AND gc_grace_seconds = 100"
    )
  )

  acceptsAll(
    "Other DDL",
    Seq(
      "CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}",
      "CREATE KEYSPACE IF NOT EXISTS ks WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3}",
      "ALTER TABLE t ADD age INT",
      "ALTER TABLE t DROP age",
      "ALTER KEYSPACE ks WITH durable_writes = true",
      "DROP TABLE IF EXISTS t",
      "DROP KEYSPACE ks",
      "CREATE TYPE addr (street TEXT, zip INT)",
      "CREATE INDEX ON t (name)",
      "CREATE INDEX idx ON ks.t (name)",
      "CREATE CUSTOM INDEX ON t (name) USING 'org.apache.cassandra.index.sasi.SASIIndex'",
      "CREATE MATERIALIZED VIEW mv AS SELECT id, name FROM t WHERE id IS NOT NULL AND name IS NOT NULL PRIMARY KEY (name, id)",
      "USE ks"
    )
  )

  acceptsAll(
    "Roles, permissions and functions",
    Seq(
      "CREATE ROLE r WITH PASSWORD = 'x' AND LOGIN = true",
      "GRANT SELECT ON KEYSPACE ks TO r",
      "GRANT ALL PERMISSIONS ON TABLE t TO r",
      "REVOKE MODIFY ON t FROM r",
      "LIST ROLES",
      "CREATE FUNCTION f (i INT) CALLED ON NULL INPUT RETURNS INT LANGUAGE java AS 'return i;'",
      "CREATE AGGREGATE agg (INT) SFUNC sf STYPE INT INITCOND 0"
    )
  )
}
