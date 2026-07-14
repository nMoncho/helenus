/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class SelectSpec extends AnyWordSpecLike with Matchers {
  "Select" should {
    "generate a SELECT for specific columns" in {
      val query = UsersTable.select(UsersTable.id, UsersTable.username, UsersTable.age).toCQL

      query shouldBe "SELECT id, username, age FROM my_keyspace.users"
    }

    "support ALLOW FILTERING" in {
      val cql = UsersTable
        .select(UsersTable.id)
        .where(UsersTable.age > 25)
        .allowFiltering
        .toCQL

      cql should endWith("ALLOW FILTERING")
    }
  }
}
