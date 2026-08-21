/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Diagnostics quality assertions.
  *
  * D1 moves the "expected ..." hint away from the hand-written previous-token table toward the
  * parser's own expected-token set: the set is rendered with friendly names (literals like `'('` /
  * `','`, keywords with the `K_` prefix stripped), all four error paths (no-viable-alt, input
  * mismatch, extraneous token, missing token) go through the same formatter, and the previous-token
  * heuristic is kept only as a fallback for sets too large to be a useful suggestion.
  */
class CqlDiagnosticsSpec extends AnyFlatSpec with Matchers {

  private def msgOf(q: String): String =
    CqlValidator.validate(q) match {
      case Left((msg, _)) => msg
      case Right(()) => fail(s"expected rejection: $q")
    }

  behavior of "CQL error diagnostics"

  it should "render a small expected set as friendly names, not raw token names" in {
    val msg = msgOf("INSERT INTO t (id) VALUES")
    msg should include("'('")
    msg should not include "LPAREN"
  }

  it should "render punctuation in the expected set as literals" in {
    val msg = msgOf("UPDATE t SET x = 1")
    msg should include("WHERE")
    msg should not include "COMMA" // rendered as ','
  }

  it should "suggest the statement keywords for a leading typo" in {
    val msg = msgOf("SELEKT * FROM t")
    msg should include("SELECT")
    msg should include("INSERT")
    msg should not include "K_SELECT" // K_ prefix stripped
  }

  it should "not dump the raw expected-token set for an extraneous token" in {
    // Previously ANTLR's default 'extraneous input' message dumped ~120 K_XXX names here.
    val msg = msgOf("SELECT FROM t")
    msg should include("'FROM'")
    msg should include("column list") // heuristic fallback for the large selector set
    msg should not include "K_"
  }

  it should "route missing-FROM through the friendly formatter" in {
    val msg = msgOf("SELECT * WHERE id = 1")
    msg should include("FROM")
    msg should not include "K_FROM"
  }

  it should "fall back to the heuristic phrase where the expected set is too large" in {
    // A value position can start many ways, so the parser's set is large; the curated phrase wins.
    msgOf("SELECT * FROM t WHERE id = foo") should include("literal value")
    msgOf("CREATE TABLE t (id)") should include("data type")
  }
}
