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
        DefaultColumnNamingScheme.map(camelCase) shouldBe camelCase
      }
    }
  }

  "SnakeCaseMapper" should {
    "map to snake case" in {
      SnakeCase.map(camelCase) shouldBe snakeCase
    }
  }

  "PascalCaseMapper" should {
    "map to pascal case" in {
      PascalCase.map("a") shouldBe "A"
      PascalCase.map("A") shouldBe "A"
      PascalCase.map(camelCase) shouldBe pascalCase
    }
  }
}
