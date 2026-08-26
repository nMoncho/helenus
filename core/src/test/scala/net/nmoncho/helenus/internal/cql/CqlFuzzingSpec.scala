/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import com.datastax.oss.driver.api.core.servererrors.SyntaxError
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

/** Grammar-driven fuzzing and property tests for [[CqlValidator]].
  *
  * Hand-written and doc-sourced corpora cover the constructs we thought of. This spec attacks
  * the long tail with generated CQL ([[CqlGenerators]]) and asserts two properties:
  *
  *   1. '''the validator never throws''', for any input at all (generated statements, mutations of
  *      them, and arbitrary garbage), `validate` / `firstErrorOffset` / `bindMarkerOffsets` return a
  *      value rather than blowing up. A parser that throws on some input is a latent compile-time
  *      crash on the `cql"..."` path.
  *   2. '''the decision matches the C2 oracle''', for generated valid CQL, the validator never
  *      rejects what a real Cassandra accepts (the harmful reject-valid direction). The benign
  *      direction (validator accepts, the 3.11 oracle rejects a 5.0 construct) is tolerated, exactly
  *      as in [[CqlDifferentialSpec]].
  *
  * Targeted generators stress the areas the plan calls out: nested/frozen types, nested collection
  * literals, and `:name` interleaved with string literals and comments.
  */
class CqlFuzzingSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with CassandraSpec {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 200)

  private def validatorAccepts(q: String): Boolean = CqlValidator.validate(q).isRight

  /** Cassandra's syntactic verdict, as in [[CqlDifferentialSpec]]: reject only on [[SyntaxError]]. */
  private def cassandraAccepts(q: String): Boolean =
    try {
      session.prepare(q)
      true
    } catch {
      case _: SyntaxError => false
      case _: Throwable => true
    }

  /** The property C3 shares with C2: the validator must never reject what Cassandra accepts. */
  private def noRejectValid(q: String): org.scalatest.Assertion =
    withClue(s"reject-valid: validator rejected CQL the oracle accepts:\n  $q\n  ") {
      (validatorAccepts(q) || !cassandraAccepts(q)) shouldBe true
    }

  private def mutate(s: String): Gen[String] =
    if (s.isEmpty) Gen.const(s)
    else
      for {
        i <- Gen.choose(0, s.length - 1)
        op <- Gen.oneOf("delete", "insert", "replace")
        c <- Gen.oneOf('?', ':', '(', ')', '<', '>', ',', ';', ' ', 'x', '1', '\'')
      } yield op match {
        case "delete" => s.take(i) + s.drop(i + 1)
        case "insert" => s.take(i) + c + s.drop(i)
        case _ => s.take(i) + c + s.drop(i + 1)
      }

  private val anyInput: Gen[String] =
    Gen.frequency(
      2 -> arbitrary[String],
      3 -> CqlGenerators.statement,
      3 -> CqlGenerators.statement.flatMap(mutate)
    )

  behavior of "CqlValidator under fuzzing"

  it should "never throw for any input" in
    forAll(anyInput) { s =>
      noException should be thrownBy {
        CqlValidator.validate(s)
        CqlValidator.firstErrorOffset(s)
        CqlValidator.bindMarkerOffsets(s)
      }
    }

  it should "never throw and report consistent offsets for generated statements" in
    forAll(CqlGenerators.statement) { q =>
      noException should be thrownBy CqlValidator.validate(q)
      // firstErrorOffset is defined iff validate fails, and points within the input.
      (CqlValidator.validate(q), CqlValidator.firstErrorOffset(q)) match {
        case (Right(_), off) => off shouldBe None
        case (Left(_), Some(o)) => o should (be >= 0 and be <= q.length)
        case (Left(_), None) => succeed // lexer-less mismatch with no offset is acceptable
      }
    }

  it should "not reject generated statements that Cassandra accepts (differential)" in
    forAll(CqlGenerators.statement)(noRejectValid)

  it should "not reject generated statements with nested/frozen types (differential)" in
    forAll(CqlGenerators.createTable)(noRejectValid)

  it should "not reject generated INSERTs with nested collection literals (differential)" in {
    val insertNested: Gen[String] =
      for {
        table <- CqlGenerators.tableName
        lit <- CqlGenerators.literal(3)
      } yield s"INSERT INTO $table (id, v) VALUES (?, $lit)"
    forAll(insertNested) { q =>
      noException should be thrownBy CqlValidator.validate(q)
      noRejectValid(q)
    }
  }

  behavior of "CqlValidator bind-marker detection under fuzzing"

  it should "not treat a ':name' inside a string literal as a bind marker" in
    forAll(CqlGenerators.ident.map(_.replace("\"", ""))) { name =>
      val q = s"SELECT * FROM t WHERE s = ':$name'"
      CqlValidator.bindMarkerOffsets(q) shouldBe empty
      noException should be thrownBy CqlValidator.validate(q)
    }

  it should "not treat a ':name' inside a block comment as a bind marker" in
    forAll(CqlGenerators.ident.map(_.replace("\"", ""))) { name =>
      val q = s"SELECT * FROM t /* :$name */ WHERE id = ?"
      CqlValidator.bindMarkerOffsets(q) shouldBe empty
    }

  it should "detect a real ':name' marker even when the same text appears in a string" in
    forAll(CqlGenerators.ident.map(_.replace("\"", ""))) { name =>
      val q = s"SELECT * FROM t WHERE a = ':$name' AND b = :$name"
      // Only the second occurrence (outside the string) is a real marker.
      CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.lastIndexOf(s":$name"))
    }
}
