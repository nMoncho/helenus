/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package integration

import org.scalatest.DoNotDiscover

@DoNotDiscover
class DdlIntegrationSpec extends CassandraIntegrationSpec {

  private def tableNames(keyspace: String): List[String] =
    rows(s"SELECT table_name FROM system_schema.tables WHERE keyspace_name = '$keyspace'")
      .map(_.getString("table_name"))

  private def columnKind(keyspace: String, table: String, column: String): String =
    rows(
      "SELECT kind FROM system_schema.columns " +
        s"WHERE keyspace_name = '$keyspace' AND table_name = '$table' AND column_name = '$column'"
    ).head.getString("kind")

  private def clusteringOrder(keyspace: String, table: String, column: String): String =
    rows(
      "SELECT clustering_order FROM system_schema.columns " +
        s"WHERE keyspace_name = '$keyspace' AND table_name = '$table' AND column_name = '$column'"
    ).head.getString("clustering_order")

  "CreateTable" should "create a real table Cassandra accepts" in {
    execute(UsersTable.drop.ifExists.toCQL)
    execute(UsersTable.create.toCQL)
    tableNames("my_keyspace") should contain("users")

    UsersTable.drop.ifExists.execute()
    tableNames("my_keyspace") should not contain "users"

    UsersTable.create.execute()
    tableNames("my_keyspace") should contain("users")
  }

  it should "be idempotent with IF NOT EXISTS" in {
    execute(UsersTable.create.ifNotExists.toCQL)
    execute(UsersTable.create.ifNotExists.toCQL)

    tableNames("my_keyspace") should contain("users")
  }

  it should "register the primary key parts of a composite partition key" in {
    execute(EventsTable.drop.ifExists.toCQL)
    execute(EventsTable.create.toCQL)

    columnKind("analytics", "events", "tenant_id") shouldBe "partition_key"
    columnKind("analytics", "events", "event_type") shouldBe "partition_key"
    columnKind("analytics", "events", "event_id") shouldBe "clustering"
    columnKind("analytics", "events", "payload") shouldBe "regular"
  }

  it should "apply the clustering order declared in CK" in {
    execute(SensorsTable.drop.ifExists.toCQL)
    execute(SensorsTable.create.toCQL)

    clusteringOrder("iot", "sensor_readings", "year") shouldBe "asc"
    clusteringOrder("iot", "sensor_readings", "ts") shouldBe "desc"
  }

  "DropTable" should "drop the table" in {
    execute("CREATE TABLE IF NOT EXISTS my_keyspace.throwaway (id int PRIMARY KEY)")
    execute("DROP TABLE my_keyspace.throwaway")

    (tableNames("my_keyspace") should not).contain("throwaway")
  }

  it should "not fail with IF EXISTS on a missing table" in {
    noException should be thrownBy execute("DROP TABLE IF EXISTS my_keyspace.throwaway")
  }
}
