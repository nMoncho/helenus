/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import java.time.Duration

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

    "emit no WITH clause when there are no options and no descending clustering" in {
      (UsersTable.create.toCQL should not).include(" WITH ")
    }

    "append a single string option, quoted" in {
      UsersTable.create.withComment("primary user table").toCQL should
      endWith(" WITH comment = 'primary user table'")
    }

    "escape single quotes inside string options" in {
      UsersTable.create.withComment("it's here").toCQL should
      include("comment = 'it''s here'")
    }

    "render numeric and boolean options unquoted" in {
      val cql = UsersTable.create
        .withGcGraceSeconds(Duration.ofSeconds(864000))
        .withBloomFilterFpChance(0.01)
        .withCdc(true)
        .toCQL
      cql should include("gc_grace_seconds = 864000")
      cql should include("bloom_filter_fp_chance = 0.01")
      cql should include("cdc = true")
    }

    "render map options as CQL string maps with sorted keys" in {
      UsersTable.create
        .withCaching(Map("rows_per_partition" -> "NONE", "keys" -> "ALL"))
        .toCQL should include("caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}")
    }

    "combine several options with AND, in canonical order regardless of call order" in {
      UsersTable.create
        .withCompaction(Map("class" -> "LeveledCompactionStrategy"))
        .withComment("c")
        .withGcGraceSeconds(Duration.ofSeconds(100))
        .toCQL should endWith(
        " WITH comment = 'c' AND gc_grace_seconds = 100 " +
          "AND compaction = {'class': 'LeveledCompactionStrategy'}"
      )
    }

    "put table options after CLUSTERING ORDER BY in one WITH clause" in {
      SensorsTable.create.withComment("readings").toCQL should
      endWith(" WITH CLUSTERING ORDER BY (year ASC, ts DESC) AND comment = 'readings'")
    }

    "support an arbitrary option via withOption, rendered verbatim" in {
      UsersTable.create.withOption("nodesync", "{'enabled': 'true'}").toCQL should
      include("nodesync = {'enabled': 'true'}")
    }
  }
}
