/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Conformance corpus check.
  *
  * Asserts [[CqlValidator]] accepts every entry of the shared [[CqlConformanceCorpus]]. It is the
  * safety net for grammar changes, in particular for re-importing a newer Cassandra grammar (see
  * `tools/antlr-import/`): if an import silently drops a construct, the corresponding entry here
  * turns red.
  *
  * The bias is deliberate: this file only asserts '''acceptance'''. Rejecting valid CQL is the
  * harmful failure mode (an unbypassable compile error on the `cql"..."` / `toCQL` path). The
  * companion [[CqlDifferentialSpec]] (C2) cross-checks the same corpus against a real Cassandra.
  */
class CqlConformanceSpec extends AnyFlatSpec with Matchers {

  behavior of "The CQL grammar"

  CqlConformanceCorpus.groups.foreach { case (group, queries) =>
    it should s"accept $group" in
    queries.foreach { q =>
      withClue(s"$q\n  ") {
        CqlValidator.validate(q) shouldBe Right(())
      }
    }
  }
}
