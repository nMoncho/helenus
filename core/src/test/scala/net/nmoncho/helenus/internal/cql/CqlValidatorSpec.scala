/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.tools.reflect.ToolBoxError

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CqlValidatorSpec extends AnyFlatSpec with Matchers {

  private lazy val tb: ToolBox[universe.type] = {
    val cl = getClass.getClassLoader
    val cp = Iterator
      .iterate(cl)(_.getParent)
      .takeWhile(_ != null)
      .collect { case u: java.net.URLClassLoader => u.getURLs }
      .flatten
      .map(_.getFile)
      .mkString(java.io.File.pathSeparator)
    universe.runtimeMirror(cl).mkToolBox(options = s"-cp $cp")
  }

  private val preamble =
    """import cql.Cql._
      |import cql.CqlSession
      |implicit val session: CqlSession = new CqlSession {}
      |""".stripMargin

  private def shouldCompile(snippet: String): Unit =
    noException should be thrownBy tb.typecheck(tb.parse(preamble + snippet))

  private def errorOf(snippet: String): String = {
    val ex = intercept[ToolBoxError](tb.typecheck(tb.parse(preamble + snippet)))
    ex.getMessage
  }

  private def errorWithoutSession(snippet: String): String = {
    val ex = intercept[ToolBoxError] {
      tb.typecheck(tb.parse(s"import cql.Cql._\n$snippet"))
    }
    ex.getMessage
  }

  private def valid(q: String): Unit =
    withClue(q)(CqlValidator.validate(q) shouldBe Right(()))

  private def invalid(q: String): (String, Int) =
    withClue(q) {
      CqlValidator.validate(q) match {
        case Left(result) => result
        case Right(()) => fail(s"Expected query to be invalid: $q")
      }
    }

  // ---------------------------------------------------------------------------
  // SELECT
  // ---------------------------------------------------------------------------

  "CqlValidator" should "accept SELECT with literals" in
    valid("SELECT id, name FROM users WHERE id = 1")

  it should "accept SELECT *" in
    valid("SELECT * FROM users")

  it should "accept SELECT with positional bind marker" in
    valid("SELECT * FROM users WHERE id = ?")

  it should "accept SELECT with named bind marker" in
    valid("SELECT * FROM users WHERE id = :id")

  it should "accept SELECT with multi-char named bind marker" in
    valid("SELECT * FROM users WHERE partition_key = :partition_key")

  it should "accept SELECT with uppercase named bind marker" in
    valid("SELECT * FROM users WHERE id = :USER_ID")

  it should "accept SELECT with keyword column names" in
    valid("SELECT date, text, int, timestamp FROM t WHERE id = ?")

  it should "accept SELECT with type keyword as column" in
    valid("SELECT type FROM t WHERE id = ?")

  it should "accept SELECT with key keyword as column" in
    valid("SELECT key FROM t WHERE id = ?")

  it should "accept SELECT DISTINCT" in
    valid("SELECT DISTINCT id FROM users")

  it should "accept SELECT with column alias" in
    valid("SELECT id AS user_id FROM users")

  it should "accept SELECT with GROUP BY" in
    valid("SELECT id, count(*) FROM users WHERE id = ? GROUP BY id")

  it should "accept SELECT with GROUP BY multiple columns" in
    valid("SELECT id, name, count(*) FROM users WHERE id = ? GROUP BY id, name")

  it should "accept SELECT with GROUP BY and ORDER BY" in
    valid("SELECT id, count(*) FROM users WHERE id = ? GROUP BY id ORDER BY id ASC")

  it should "accept SELECT with ORDER BY ASC" in
    valid("SELECT id FROM users WHERE id = ? ORDER BY id ASC")

  it should "accept SELECT with ORDER BY DESC and LIMIT" in
    valid("SELECT id FROM users WHERE id = ? ORDER BY id DESC LIMIT 100")

  it should "accept SELECT with PER PARTITION LIMIT" in
    valid("SELECT id FROM users WHERE id = ? PER PARTITION LIMIT 10")

  it should "accept SELECT with PER PARTITION LIMIT and LIMIT" in
    valid("SELECT id FROM users WHERE id = ? PER PARTITION LIMIT 10 LIMIT 100")

  it should "accept SELECT with GROUP BY, ORDER BY, PER PARTITION LIMIT and LIMIT" in
    valid(
      "SELECT id, count(*) FROM users WHERE id = ? GROUP BY id ORDER BY id ASC PER PARTITION LIMIT 10 LIMIT 100"
    )

  it should "accept SELECT with LIKE" in
    valid("SELECT id FROM users WHERE name LIKE 'jo%'")

  it should "accept SELECT with LIKE and bind marker" in
    valid("SELECT id FROM users WHERE name LIKE ?")

  it should "accept SELECT with LIKE combined with AND" in
    valid("SELECT id FROM users WHERE id = ? AND name LIKE 'jo%'")

  it should "accept SELECT with CAST" in
    valid("SELECT CAST(count AS DOUBLE) FROM users WHERE id = ?")

  it should "accept SELECT with CAST nested inside a function call" in
    valid("SELECT avg(CAST(count AS DOUBLE)) FROM users WHERE id = ?")

  it should "reject CAST on the left-hand side of a WHERE relation (not valid CQL)" in
    // A relation is `column operator term`; CAST is only a selector or a
    // right-hand term, never the left-hand side. The old hand-grammar wrongly
    // accepted this; Cassandra's grammar (correctly) rejects it.
    invalid("SELECT id FROM users WHERE CAST(count AS INT) = 1")

  it should "accept SELECT with ALLOW FILTERING" in
    valid("SELECT * FROM users WHERE id = ? AND name = ? ALLOW FILTERING")

  it should "accept SELECT with IN clause" in
    valid("SELECT * FROM users WHERE id IN (1, 2, 3)")

  it should "accept SELECT with IN and bind markers" in
    valid("SELECT * FROM users WHERE id IN (?, ?, ?)")

  it should "accept SELECT with qualified table name" in
    valid("SELECT id FROM mykeyspace.users WHERE id = ?")

  // ---------------------------------------------------------------------------
  // INSERT
  // ---------------------------------------------------------------------------

  it should "accept INSERT with literal values" in
    valid("INSERT INTO users (id, name) VALUES (1, 'Alice')")

  it should "accept INSERT with positional bind markers" in
    valid("INSERT INTO users (id, name) VALUES (?, ?)")

  it should "accept INSERT with named bind markers" in
    valid("INSERT INTO users (id, name) VALUES (:id, :name)")

  it should "accept INSERT IF NOT EXISTS" in
    valid("INSERT INTO users (id) VALUES (?) IF NOT EXISTS")

  it should "accept INSERT with TTL" in
    valid("INSERT INTO users (id) VALUES (?) USING TTL 86400")

  it should "accept INSERT with keyword column names" in
    valid("INSERT INTO t (id, date, text) VALUES (?, ?, ?)")

  it should "reject INSERT with UNSET as a literal value (not valid CQL)" in
    // UNSET is a bound protocol value, not a term you can write in CQL text
    // (`UNSET` is only an unreserved keyword usable as an identifier). The old
    // hand-grammar wrongly accepted it; Cassandra's grammar rejects it.
    invalid("INSERT INTO users (id, name) VALUES (1, UNSET)")

  // ---------------------------------------------------------------------------
  // UPDATE
  // ---------------------------------------------------------------------------

  it should "accept UPDATE with literal value" in
    valid("UPDATE users SET name = 'Bob' WHERE id = 1")

  it should "accept UPDATE with positional bind markers" in
    valid("UPDATE users SET name = ? WHERE id = ?")

  it should "accept UPDATE with named bind markers" in
    valid("UPDATE users SET name = :name WHERE id = :id")

  it should "accept UPDATE with keyword column names" in
    valid("UPDATE t SET date = ?, text = ?, int = ? WHERE id = ?")

  it should "accept UPDATE with multiple SET assignments" in
    valid("UPDATE users SET name = ?, email = ? WHERE id = ?")

  it should "accept UPDATE with counter increment" in
    valid("UPDATE t SET counter_col = counter_col + 1 WHERE id = ?")

  it should "reject UPDATE with UNSET as a literal value (not valid CQL)" in
    // See the INSERT UNSET case above: UNSET is bound, not written in CQL text.
    invalid("UPDATE users SET name = UNSET WHERE id = ?")

  // ---------------------------------------------------------------------------
  // DELETE
  // ---------------------------------------------------------------------------

  it should "accept DELETE with positional bind marker" in
    valid("DELETE FROM users WHERE id = ?")

  it should "accept DELETE with named bind marker" in
    valid("DELETE FROM users WHERE id = :id")

  it should "accept DELETE specific columns" in
    valid("DELETE name FROM users WHERE id = ?")

  it should "accept DELETE with keyword column name" in
    valid("DELETE date FROM t WHERE id = ?")

  // ---------------------------------------------------------------------------
  // DDL
  // ---------------------------------------------------------------------------

  it should "accept CREATE TABLE IF NOT EXISTS" in
    valid("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, name TEXT)")

  it should "accept CREATE TABLE with keyword column names" in
    valid("CREATE TABLE t (id UUID PRIMARY KEY, date TIMESTAMP, text TEXT, int INT)")

  it should "accept CREATE TABLE with composite primary key" in
    valid("CREATE TABLE t (id UUID, cluster_key TEXT, val INT, PRIMARY KEY (id, cluster_key))")

  it should "accept CREATE KEYSPACE with SimpleStrategy" in
    valid(
      "CREATE KEYSPACE IF NOT EXISTS ks WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}"
    )

  it should "accept CREATE KEYSPACE with NetworkTopologyStrategy" in
    valid(
      "CREATE KEYSPACE ks WITH REPLICATION = {'class': 'NetworkTopologyStrategy', 'dc1': 3} AND DURABLE_WRITES = true"
    )

  it should "accept DROP TABLE IF EXISTS" in
    valid("DROP TABLE IF EXISTS users")

  it should "accept TRUNCATE" in
    valid("TRUNCATE users")

  it should "accept TRUNCATE TABLE" in
    valid("TRUNCATE TABLE users")

  // ---------------------------------------------------------------------------
  // Bind markers: named vs COLON in map literals
  // ---------------------------------------------------------------------------

  it should "not confuse COLON in replication map with named bind marker" in
    valid(
      "CREATE KEYSPACE ks WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}"
    )

  it should "accept named bind markers alongside map literal colons" in
    valid("SELECT * FROM t WHERE id = :id")

  // ---------------------------------------------------------------------------
  // Multiline strings (content already stripped, validator sees the result)
  // ---------------------------------------------------------------------------

  it should "accept a multi-line SELECT passed as a single string" in
    valid("SELECT id, name\nFROM users\nWHERE id = ?")

  it should "accept a multi-line INSERT with leading whitespace stripped" in
    valid("INSERT INTO users (id, name)\nVALUES (?, ?)")

  // ---------------------------------------------------------------------------
  // Error cases: messages
  // ---------------------------------------------------------------------------

  it should "reject a statement keyword typo" in {
    val (msg, _) = invalid("SELEKT * FROM users")
    msg should include("SELEKT")
    msg should include("SELECT") // "did you mean 'SELECT'?"
  }

  it should "reject INSERT keyword typo with suggestion" in {
    val (msg, _) = invalid("INSERTT INTO t (id) VALUES (?)")
    msg should include("INSERT")
  }

  it should "reject a bare identifier in a value position" in {
    val (msg, _) = invalid("SELECT * FROM users WHERE id = foo")
    msg should include("foo")
    msg should include("literal value")
  }

  it should "reject a missing data type in CREATE TABLE" in {
    val (msg, _) = invalid("CREATE TABLE t (id)")
    msg should include("data type")
  }

  // ---------------------------------------------------------------------------
  // Error cases: character position
  // ---------------------------------------------------------------------------

  it should "report position 0 for a leading keyword typo" in {
    val (_, pos) = invalid("SELEKT * FROM users")
    pos shouldBe 0
  }

  it should "report the correct position for a mid-query error" in {
    val query    = "SELECT * FROM users WHERE id = foo"
    val (_, pos) = invalid(query)
    pos shouldBe query.indexOf("foo")
  }

  // ---------------------------------------------------------------------------
  // Extended valid-CQL corpus: constructs the grammar accepts today.
  // ---------------------------------------------------------------------------

  it should "accept INSERT with USING TIMESTAMP" in
    valid("INSERT INTO t (id) VALUES (?) USING TIMESTAMP 123")

  it should "accept UPDATE with USING TTL" in
    valid("UPDATE t USING TTL 100 SET a = ? WHERE id = ?")

  it should "accept INSERT with a set literal value" in
    valid("INSERT INTO t (id, tags) VALUES (?, {'a', 'b'})")

  it should "accept INSERT with a map literal value" in
    valid("INSERT INTO t (id, m) VALUES (?, {'k': 'v'})")

  it should "accept INSERT with a list literal value" in
    valid("INSERT INTO t (id, l) VALUES (?, [1, 2, 3])")

  it should "accept INSERT with a tuple literal value" in
    valid("INSERT INTO t (id, tup) VALUES (?, (1, 'a'))")

  it should "accept INSERT with a function-call value" in
    valid("INSERT INTO t (id, ts) VALUES (?, now())")

  it should "accept SELECT with CONTAINS" in
    valid("SELECT * FROM t WHERE tags CONTAINS 'x' ALLOW FILTERING")

  it should "accept SELECT with CONTAINS KEY" in
    valid("SELECT * FROM t WHERE m CONTAINS KEY 'k' ALLOW FILTERING")

  it should "accept SELECT with a tuple IN clause" in
    valid("SELECT * FROM t WHERE (a, b) IN ((1, 2), (3, 4))")

  it should "accept SELECT with a negative literal" in
    valid("SELECT * FROM t WHERE n = -1")

  it should "accept SELECT JSON" in
    valid("SELECT JSON * FROM t")

  it should "accept INSERT JSON" in
    valid("INSERT INTO t JSON '{\"id\": 1}'")

  it should "accept CREATE TABLE with collection column types" in
    valid("CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, INT>, l LIST<INT>, s SET<TEXT>)")

  it should "accept CREATE TABLE with a static column" in
    valid("CREATE TABLE t (id INT, c INT, s TEXT STATIC, PRIMARY KEY (id, c))")

  it should "accept CREATE TABLE with CLUSTERING ORDER" in
    valid(
      "CREATE TABLE t (id INT, c INT, PRIMARY KEY (id, c)) WITH CLUSTERING ORDER BY (c DESC)"
    )

  it should "accept UPDATE with an LWT IF condition" in
    valid("UPDATE t SET name = ? WHERE id = ? IF name = ?")

  it should "accept DELETE with IF EXISTS" in
    valid("DELETE FROM t WHERE id = ? IF EXISTS")

  it should "accept CREATE INDEX" in
    valid("CREATE INDEX ON t (name)")

  it should "accept CREATE TYPE" in
    valid("CREATE TYPE address (street TEXT, zip INT)")

  it should "accept ALTER TABLE ADD" in
    valid("ALTER TABLE t ADD age INT")

  it should "accept USE keyspace" in
    valid("USE my_keyspace")

  // ---------------------------------------------------------------------------
  // Formerly-tracked grammar gaps, now covered.
  //
  // These four constructs were rejected by the previous hand-maintained grammar and tracked here as
  // known gaps. Switching to Cassandra's own grammar (see tools/antlr-import/) closes all of them,
  // so they are now asserted as valid.
  // ---------------------------------------------------------------------------

  it should "accept multi-statement BATCH" in
    valid("BEGIN BATCH INSERT INTO t (id) VALUES (?) INSERT INTO t (id) VALUES (?) APPLY BATCH")

  it should "reject a BATCH missing its APPLY BATCH (A3)" in
    // The `batchStatement` rule requires the closing `APPLY BATCH`, so an unterminated block is a
    // syntax error rather than being silently accepted.
    invalid("BEGIN BATCH INSERT INTO t (id) VALUES (?) INSERT INTO t (id) VALUES (?)")

  it should "accept token() in a WHERE relation" in
    valid("SELECT * FROM t WHERE token(id) > token(?)")

  it should "accept FROZEN parameterized collection types" in
    valid("CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<LIST<INT>>)")

  it should "accept nested parameterized collection types" in
    valid("CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<LIST<INT>>>)")

  it should "accept WRITETIME/TTL selectors in SELECT" in
    valid("SELECT WRITETIME(name), TTL(name) FROM t WHERE id = ?")

  // ---------------------------------------------------------------------------
  // Tracked grammar gaps.
  //
  // Constructs the validator rejects even though Cassandra accepts them, kept here with a rationale
  // and an escape hatch (per F2, each asserts the *current* rejection, so closing the gap fails its
  // test and forces the docs to be updated in lockstep).
  //
  // Gap: a bind marker (`?` or `:name`) used as a *function argument in the SELECT selector list*
  // (e.g. `similarity_cosine(v, ?)`). This is a direct consequence of the intentional deviation
  // "bind markers are not valid as SELECT selectors" (see tools/antlr-import/README.md): selector
  // function arguments resolve through `unaliasedSelector`, from which the marker alternatives were
  // removed so the interpolator injects an identifier there rather than binding a value. Only bind
  // markers are affected, column, constant, string and collection-literal arguments are accepted.
  // Escape hatch: `"...".toUnsafeCQL`.
  // ---------------------------------------------------------------------------

  it should "reject a bind marker as a SELECT-selector function argument (tracked gap A4)" in {
    invalid("SELECT id, similarity_cosine(v, ?) FROM t")
    invalid("SELECT f(?) FROM t")
    invalid("SELECT f(:name) FROM t")
  }

  it should "accept non-marker function arguments in SELECT-selector position (gap is marker-only)" in {
    // Confirms the gap is narrow: everything except a bind marker is fine in this position.
    valid("SELECT f(a) FROM t")
    valid("SELECT f(1) FROM t")
    valid("SELECT similarity_cosine(v, [0.1, 0.2]) FROM t")
  }

  // ---------------------------------------------------------------------------
  // Bind-inference primitives: `bindMarkerOffsets` and `firstErrorOffset` drive the interpolator's
  // bind-vs-inject decision, so their behaviour is pinned here directly.
  // ---------------------------------------------------------------------------

  it should "report the offset of a named bind marker" in {
    val q = "SELECT * FROM t WHERE id = :id"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "report every named bind marker offset" in {
    val q = "SELECT * FROM t WHERE a = :a AND b = :b"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":a"), q.indexOf(":b"))
  }

  it should "not treat a positional '?' as a named bind marker" in {
    CqlValidator.bindMarkerOffsets("SELECT * FROM t WHERE id = ?") shouldBe empty
  }

  it should "not treat a colon inside a string literal as a named bind marker" in {
    CqlValidator.bindMarkerOffsets("SELECT * FROM t WHERE s = ':id'") shouldBe empty
  }

  it should "not treat a map-literal colon as a named bind marker" in {
    CqlValidator.bindMarkerOffsets(
      "CREATE KEYSPACE ks WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}"
    ) shouldBe empty
  }

  it should "report no first-error offset for a valid statement" in {
    CqlValidator.firstErrorOffset("SELECT * FROM t WHERE id = ?") shouldBe None
  }

  it should "report the absolute first-error offset for an invalid statement" in {
    val q = "SELECT * FROM t WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "report the absolute (not in-line) offset on a multi-line statement" in {
    val q = "SELECT *\nFROM t WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }
}
