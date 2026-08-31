/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CreateTableSpec extends AnyWordSpec with Matchers {

  "CreateTable" should {
    "generate a simple CREATE TABLE statement" in {
      UsersTable.create.toCQL shouldBe
      "CREATE TABLE my_keyspace.users " +
      "(id uuid, username text, age int, email text, tags frozen<set<text>>, metadata map<text, text>, " +
      "PRIMARY KEY (id, username))"
    }

    "support IF NOT EXISTS" in {
      UsersTable.create.ifNotExists.toCQL should startWith("CREATE TABLE IF NOT EXISTS")
    }

    "generate a composite partition key" in {
      EventsTable.create.toCQL should include("PRIMARY KEY ((tenant_id, event_type), event_id)")
    }

    "derive WITH CLUSTERING ORDER BY from the CK declaration" in {
      SensorsTable.create.toCQL should
      endWith("WITH CLUSTERING ORDER BY (year ASC, ts DESC)")
    }

    "omit WITH CLUSTERING ORDER BY when every clustering column is ascending" in {
      (UsersTable.create.toCQL should not).include("CLUSTERING ORDER BY")
    }

    "render a Frozen field as frozen<...>" in {
      SnapshotsTable.create.toCQL shouldBe
      "CREATE TABLE blog.snapshots " +
      "(id uuid, labels frozen<set<text>>, tags frozen<set<text>>, " +
      "PRIMARY KEY (id))"
    }

    "render a static column with the STATIC keyword" in {
      TransactionsTable.create.toCQL shouldBe
      "CREATE TABLE banking.transactions " +
      "(account_id uuid, tx_id uuid, account_name text STATIC, amount double, " +
      "PRIMARY KEY (account_id, tx_id))"
    }

    "append STATIC only to the static column, leaving the others unchanged" in {
      val cql = TransactionsTable.create.toCQL
      cql should include("account_name text STATIC")
      (cql should not).include("amount double STATIC")
      (cql should not).include("account_id uuid STATIC")
    }
  }
}
