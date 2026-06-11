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

  it should "accept SELECT with ORDER BY ASC" in
    valid("SELECT id FROM users WHERE id = ? ORDER BY id ASC")

  it should "accept SELECT with ORDER BY DESC and LIMIT" in
    valid("SELECT id FROM users WHERE id = ? ORDER BY id DESC LIMIT 100")

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
}
