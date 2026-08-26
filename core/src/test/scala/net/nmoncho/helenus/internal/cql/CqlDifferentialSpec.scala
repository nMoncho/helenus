/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import com.datastax.oss.driver.api.core.servererrors.SyntaxError
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Differential (oracle) testing of [[CqlValidator]] against a real Cassandra.
  *
  * A large corpus only encodes '''our''' assumptions about what valid CQL is. This spec removes that
  * bias by asking a real Cassandra, the in-process embedded server the test suite already starts,
  * the same accept/reject question, for every entry of [[CqlConformanceCorpus]].
  *
  * ==The oracle==
  *
  * `session.prepare(query)` is used as the syntactic oracle. It is side-effect free even for DDL
  * (the server parses and prepares but does not execute), and it cleanly separates the two failure
  * kinds:
  *
  *   - a [[SyntaxError]] means Cassandra '''rejects the syntax''';
  *   - anything else, a successful prepare, or an `InvalidQueryException` such as "unconfigured
  *     table", means the '''syntax is fine''' and only semantics (a missing table/keyspace/column)
  *     failed. Since [[CqlValidator]] is syntactic-only, a semantic failure counts as acceptance.
  *
  * ==The asymmetry==
  *
  * Following the two disagreement directions are treated differently:
  *
  *   - '''validator rejects, Cassandra accepts''' is a hard failure: the validator would turn valid
  *     CQL into an unbypassable compile error. This is the property C2 guards.
  *   - '''validator accepts, Cassandra rejects''' is benign and only logged. The embedded server is
  *     Cassandra 3.11 while the grammar targets 5.0, so genuinely-valid 5.0 constructs (e.g.
  *     `VECTOR<FLOAT, n>`) are expected to be rejected by the older oracle; that is not a bug.
  */
class CqlDifferentialSpec extends AnyFlatSpec with Matchers with CassandraSpec {

  behavior of "CqlValidator against a real Cassandra"

  /** Cassandra's '''syntactic''' verdict: `true` unless preparing raises a [[SyntaxError]]. */
  private def cassandraAccepts(query: String): Boolean =
    try {
      session.prepare(query)
      true
    } catch {
      case _: SyntaxError => false
      // Any non-syntax outcome (unconfigured table, unknown keyspace, timeout, ...) means the
      // syntax parsed; only semantics or the environment failed. Treat as acceptance.
      case _: Throwable => true
    }

  private def validatorAccepts(query: String): Boolean =
    CqlValidator.validate(query).isRight

  it should "have a working oracle (sanity check)" in {
    // Guards the differential mechanism itself: if these classifications ever flip, the oracle is
    // broken and every other assertion below is meaningless.
    cassandraAccepts("SELECT * FROM system.local") shouldBe true
    cassandraAccepts(
      "SELECT id FROM does_not_exist WHERE id = ?"
    ) shouldBe true // semantic, not syntax
    cassandraAccepts("SELEKT * FROM t") shouldBe false
    cassandraAccepts("SELECT * FROM") shouldBe false
  }

  it should "never reject CQL that Cassandra accepts (over the conformance corpus)" in {
    val rejectValid = CqlConformanceCorpus.queries.filterNot { q =>
      // Agreement (or benign accept/reject) is fine; only reject-valid is a defect.
      validatorAccepts(q) || !cassandraAccepts(q)
    }

    // Log the benign direction so the version gap between the 3.11 oracle and the 5.0 grammar is
    // visible rather than silent.
    val benign =
      CqlConformanceCorpus.queries.filter(q => validatorAccepts(q) && !cassandraAccepts(q))
    info(
      s"${benign.size} benign divergence(s) (validator accepts, embedded Cassandra rejects, " +
        "expected for 5.0-only syntax):"
    )
    benign.foreach(q => info(s"  - $q"))

    withClue(
      "Validator REJECTED CQL the embedded Cassandra ACCEPTS (this is the harmful direction):\n" +
        rejectValid.map("  - " + _).mkString("\n") + "\n"
    ) {
      rejectValid shouldBe empty
    }
  }

  // The statements [[CqlValidator]] deliberately rejects (see the pinned cases in
  // `CqlValidatorSpec`). Cross-checking them against the oracle makes this spec bidirectional: it
  // confirms an intentional rejection is not secretly a reject-valid defect. If Cassandra ever
  // accepts one of these, the validator is rejecting valid CQL and the test fails.
  private val intentionalRejections = Seq(
    "SELECT id FROM users WHERE CAST(count AS INT) = 1",
    "INSERT INTO users (id, name) VALUES (1, UNSET)",
    "UPDATE users SET name = UNSET WHERE id = ?"
  )

  it should "reject only CQL that Cassandra also rejects (intentional rejections cross-check)" in
    intentionalRejections.foreach { q =>
      withClue(s"$q\n  validator and Cassandra must agree this is invalid, ") {
        validatorAccepts(q) shouldBe false
        cassandraAccepts(q) shouldBe false
      }
    }

  // Guards the C4 negative corpus against a mislabelled valid statement: every entry must be
  // rejected by a real Cassandra too, so `CqlNegativeSpec` is asserting diagnostics on genuinely
  // invalid CQL rather than accidentally pinning a reject-valid defect.
  it should "confirm the C4 negative corpus is genuinely invalid (oracle rejects it too)" in
    CqlNegativeCorpus.queries.foreach { q =>
      withClue(s"$q\n  the negative corpus must contain only CQL Cassandra also rejects, ") {
        validatorAccepts(q) shouldBe false
        cassandraAccepts(q) shouldBe false
      }
    }
}
