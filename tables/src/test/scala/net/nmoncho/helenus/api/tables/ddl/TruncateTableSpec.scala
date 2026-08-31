/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TruncateTableSpec extends AnyWordSpecLike with Matchers {

  "TruncateTable" should {
    "generate a TRUNCATE TABLE statement" in {
      UsersTable.truncate.toCQL shouldBe "TRUNCATE TABLE my_keyspace.users"
    }

    "use the table's fully-qualified name" in {
      SensorsTable.truncate.toCQL shouldBe "TRUNCATE TABLE iot.sensor_readings"
    }

    "render the same via toString" in {
      UsersTable.truncate.toString shouldBe "TRUNCATE TABLE my_keyspace.users"
    }
  }

}
