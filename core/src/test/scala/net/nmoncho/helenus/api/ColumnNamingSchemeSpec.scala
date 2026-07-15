/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ColumnNamingSchemeSpec extends AnyWordSpec with Matchers {

  private val camelCase  = "numCherries"
  private val snakeCase  = "num_cherries"
  private val pascalCase = "NumCherries"

  "DefaultColumnMapper" should {
    "map to camel case" in {
      withClue("the assumed starting point is camel case") {
        DefaultColumnNamingScheme.apply(camelCase) shouldBe camelCase
      }
    }
  }

  "SnakeCaseMapper" should {
    "map to snake case" in {
      SnakeCase.apply(camelCase) shouldBe snakeCase
    }
  }

  "PascalCaseMapper" should {
    "map to pascal case" in {
      PascalCase.apply("a") shouldBe "A"
      PascalCase.apply("A") shouldBe "A"
      PascalCase.apply(camelCase) shouldBe pascalCase
    }
  }
}
