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

// Tests that verify compile-time macro behaviour by driving a ToolBox compiler.
// Each tb.typecheck() call triggers a full macro expansion; c.abort() in the
// macro surfaces as a ToolBoxError whose message contains the compiler diagnostic.
class CqlMacroSpec extends AnyFlatSpec with Matchers {

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
    """import net.nmoncho.helenus._
      |import com.datastax.oss.driver.api.core.CqlSession
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
      tb.typecheck(tb.parse(s"import net.nmoncho.helenus._\n$snippet"))
    }
    ex.getMessage
  }

  // ---------------------------------------------------------------------------
  // cql) macro — valid queries
  // ---------------------------------------------------------------------------

  "cql) macro" should "compile SELECT with literal" in
    shouldCompile("""cql"SELECT id FROM users WHERE id = ?"""")

  it should "compile INSERT with bind markers" in
    shouldCompile("""cql"INSERT INTO users (id, name) VALUES (?, ?)"""")

  it should "compile UPDATE with named bind markers" in
    shouldCompile("""cql"UPDATE users SET name = :name WHERE id = :id"""")

  it should "compile DELETE with named bind marker" in
    shouldCompile("""cql"DELETE FROM users WHERE id = :id"""")

  it should "compile CREATE TABLE" in
    shouldCompile("""cql"CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, name TEXT)"""")

  it should "compile CREATE KEYSPACE with replication map" in
    shouldCompile(
      """cql"CREATE KEYSPACE ks WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}""""
    )

  it should "compile SELECT with keyword column names" in
    shouldCompile("""cql"SELECT date FROM cql_interpolation_test WHERE id = :id"""")

  it should "compile SELECT with multiple keyword column names" in
    shouldCompile("""cql"SELECT date, text, int, timestamp FROM t WHERE id = ?"""")

  it should "compile UPDATE SET with keyword column names" in
    shouldCompile("""cql"UPDATE t SET date = ?, text = ? WHERE id = ?"""")

  // ---------------------------------------------------------------------------
  // cql) macro — invalid queries (compile-time errors)
  // ---------------------------------------------------------------------------

  it should "reject a statement keyword typo" in {
    val msg = errorOf("""cql"SELEKT id FROM users"""")
    msg should include("Invalid CQL")
    msg should include("SELEKT")
  }

  it should "suggest the correct keyword for a typo" in {
    val msg = errorOf("""cql"SELEKT id FROM users"""")
    msg should include("SELECT")
  }

  it should "reject a bare identifier in a value position" in {
    val msg = errorOf("""cql"SELECT * FROM users WHERE id = foo"""")
    msg should include("Invalid CQL")
    msg should include("foo")
  }

  it should "reject a query with a missing data type in CREATE TABLE" in {
    val msg = errorOf("""cql"CREATE TABLE t (id)"""")
    msg should include("Invalid CQL")
  }

  // ---------------------------------------------------------------------------
  // toCQL extension method — valid queries
  // ---------------------------------------------------------------------------

  "toCQL extension method" should "compile a valid SELECT literal" in
    shouldCompile(""""SELECT id FROM users WHERE id = ?".toCQL""")

  it should "compile a valid INSERT literal" in
    shouldCompile(""""INSERT INTO users (id, name) VALUES (:id, :name)".toCQL""")

  it should "compile with keyword column name" in
    shouldCompile(""""SELECT date FROM t WHERE id = ?".toCQL""")

  // ---------------------------------------------------------------------------
  // toCQL — stripMargin / trim chains
  // ---------------------------------------------------------------------------

  it should "compile a multiline string with stripMargin" in
    shouldCompile(
      s"""${'"'}${'"'}${'"'}SELECT id, name
        |FROM users
        |WHERE id = ?${'"'}${'"'}${'"'}.stripMargin.toCQL"""
    )

  it should "compile a string with trim" in
    shouldCompile(""""  SELECT id FROM users WHERE id = ?  ".trim.toCQL""")

  it should "reject invalid CQL in a stripMargin string" in {
    val code =
      "\"\"\"SELEKT id\n      |FROM users\"\"\".stripMargin.toCQL"
    val msg = errorOf(code)
    msg should include("Invalid CQL")
    msg should include("SELEKT")
  }

  // ---------------------------------------------------------------------------
  // toCQL — CqlSession implicit requirement
  // ---------------------------------------------------------------------------

  it should "require an implicit CqlSession in scope" in {
    val msg = errorWithoutSession(""""SELECT id FROM users".toCQL""")
    msg should include("CqlSession")
  }

  it should "compile when an implicit CqlSession is provided" in
    shouldCompile(""""SELECT id FROM users WHERE id = ?".toCQL""")

  // ---------------------------------------------------------------------------
  // toCQL — non-literal rejection
  // ---------------------------------------------------------------------------

  it should "reject a runtime variable" in {
    val msg = errorOf(
      """val q: String = "SELECT id FROM users"
        |q.toCQL
        |""".stripMargin
    )
    msg should (include("compile-time") or include("literal"))
  }

  // ---------------------------------------------------------------------------
  // Error message quality
  // ---------------------------------------------------------------------------

  "error diagnostics" should "suggest 'SELECT' for 'SELEKT'" in {
    val msg = errorOf("""cql"SELEKT id FROM users"""")
    msg should include(
      "mismatched input 'SELEKT' expecting {<EOF>, ';', '--', 'ALTER', 'APPLY', 'BEGIN', 'CREATE', 'DELETE', 'DROP', 'GRANT', 'INSERT', 'REVOKE', 'SELECT', 'TRUNCATE', 'UPDATE', 'USE', 'LIST'}"
    )
  }

  it should "suggest 'INSERT' for 'INSERTT'" in {
    val msg = errorOf("""cql"INSERTT INTO t (id) VALUES (?)"""")
    msg should include(
      "mismatched input 'INSERTT' expecting {<EOF>, ';', '--', 'ALTER', 'APPLY', 'BEGIN', 'CREATE', 'DELETE', 'DROP', 'GRANT', 'INSERT', 'REVOKE', 'SELECT', 'TRUNCATE', 'UPDATE', 'USE', 'LIST'}"
    )
  }

  it should "describe expected value when a bare identifier is used" in {
    val msg = errorOf("""cql"SELECT * FROM users WHERE id = foo"""")
    msg should include("literal value")
  }
}
