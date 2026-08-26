/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

/** Curated negative corpus for the CQL validator.
  *
  * Invalid CQL, typos, missing clauses, wrong ordering, and malformed fragments, that
  * [[CqlValidator]] must '''reject''', paired with the position and a stable, useful fragment of the
  * message. It regression-protects the diagnostics (Workstream D) and locks in the rejection
  * behaviour, so a grammar change that silently starts accepting garbage, or degrades a message, is
  * caught.
  *
  * Every entry is genuinely invalid CQL: [[CqlDifferentialSpec]] cross-checks that a real Cassandra
  * rejects each one too, so nothing here is a mislabelled valid statement (which would be a harmful
  * reject-valid case, not a diagnostics regression).
  *
  * `messageContains` fragments are chosen to be stable and meaningful (the offending token, or a
  * human-readable "expected ..." phrase). Some entries hit the previous-token heuristic's known weak
  * spots (D1), where the phrasing is imperfect but the token and position are correct; those assert
  * the token/position rather than the misleading phrase.
  */
object CqlNegativeCorpus {

  final case class NegativeCase(query: String, position: Int, messageContains: Seq[String])

  val cases: Seq[NegativeCase] = Seq(
    // ---- statement keyword typos (should suggest the intended keyword) ----
    NegativeCase("SELEKT * FROM t", 0, Seq("'SELEKT'", "SELECT")),
    NegativeCase("INSERTT INTO t (id) VALUES (?)", 0, Seq("'INSERTT'", "INSERT")),
    NegativeCase("DELET FROM t WHERE id = 1", 0, Seq("'DELET'", "DELETE")),
    NegativeCase("CREAT TABLE t (id INT PRIMARY KEY)", 0, Seq("'CREAT'", "CREATE")),
    NegativeCase("TRUNCAT t", 0, Seq("'TRUNCAT'", "TRUNCATE")),
    NegativeCase("UPDATE t SETT x = 1 WHERE id = 1", 9, Seq("'SETT'")),
    // ---- missing clauses ----
    NegativeCase("SELECT FROM t", 7, Seq("'FROM'", "column list")),
    NegativeCase("SELECT * FROM", 13, Seq("table name")),
    NegativeCase("SELECT * WHERE id = 1", 9, Seq("'WHERE'", "FROM")),
    NegativeCase("INSERT INTO t (id) VALUES", 25, Seq("end of input", "'('")),
    NegativeCase("INSERT INTO t VALUES (1)", 14, Seq("'VALUES'")),
    NegativeCase("UPDATE t SET x = 1", 18, Seq("WHERE")),
    NegativeCase("DELETE FROM t", 13, Seq("end of input")),
    NegativeCase("CREATE TABLE t (id)", 18, Seq("data type")),
    NegativeCase("BEGIN BATCH INSERT INTO t (id) VALUES (?)", 41, Seq("APPLY")),
    // ---- wrong clause ordering ----
    NegativeCase("SELECT * FROM t LIMIT 10 WHERE id = 1", 25, Seq("'WHERE'")),
    NegativeCase("SELECT * FROM t ORDER BY id WHERE id = 1", 28, Seq("'WHERE'")),
    NegativeCase("SELECT * FROM t ALLOW FILTERING WHERE id = 1", 32, Seq("'WHERE'")),
    NegativeCase("FROM t SELECT *", 0, Seq("'FROM'")),
    NegativeCase("UPDATE t WHERE id = 1 SET x = 2", 9, Seq("'WHERE'")),
    NegativeCase("INSERT INTO t IF NOT EXISTS (id) VALUES (?)", 14, Seq("'IF'")),
    // ---- malformed fragments ----
    NegativeCase("SELECT * FROM t WHERE id = foo", 27, Seq("'foo'", "literal value")),
    NegativeCase("SELECT * FROM t WHERE id = ", 27, Seq("end of input", "literal value")),
    NegativeCase("INSERT INTO t (id) VALUES (1", 28, Seq("end of input")),
    NegativeCase("SELECT * FROM t WHERE", 21, Seq("relation")),
    NegativeCase("SELECT , FROM t", 7, Seq("column list")),
    NegativeCase("SELECT * FROM t t2 WHERE id = 1", 16, Seq("'t2'"))
  )

  /** Just the invalid queries, for callers that only need the strings. */
  val queries: Seq[String] = cases.map(_.query)
}
