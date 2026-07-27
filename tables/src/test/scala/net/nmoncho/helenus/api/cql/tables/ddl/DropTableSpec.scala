/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables
package ddl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DropTableSpec extends AnyWordSpecLike with Matchers {

  "DropTable" should {
    "generate a DROP TABLE statement" in {
      UsersTable.drop.toCQL shouldBe "DROP TABLE my_keyspace.users"
    }

    "support IF EXISTS" in {
      UsersTable.drop.ifExists.toCQL shouldBe "DROP TABLE IF EXISTS my_keyspace.users"
    }
  }

}
