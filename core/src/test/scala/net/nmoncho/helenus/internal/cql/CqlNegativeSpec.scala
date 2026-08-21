/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Negative-corpus and error-message regressions.
  *
  * Asserts that every entry of the shared [[CqlNegativeCorpus]] is rejected by [[CqlValidator]] at
  * the expected position and with a stable, useful message fragment. This locks the diagnostics
  * (Workstream D) against regressions and ensures the validator does not silently start accepting
  * malformed CQL.
  */
class CqlNegativeSpec extends AnyFlatSpec with Matchers {

  behavior of "The CQL validator on invalid input"

  CqlNegativeCorpus.cases.foreach { c =>
    it should s"reject: ${c.query}" in {
      CqlValidator.validate(c.query) match {
        case Right(()) =>
          fail(s"expected rejection but the validator accepted: ${c.query}")
        case Left((msg, pos)) =>
          withClue(s"query=[${c.query}] msg=[$msg] pos=$pos\n  ") {
            pos shouldBe c.position
            c.messageContains.foreach(fragment => msg should include(fragment))
          }
      }
    }
  }
}
