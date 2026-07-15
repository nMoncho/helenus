/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.integration

import java.util.UUID

import net.nmoncho.helenus.api.cql.Metric
import net.nmoncho.helenus.api.cql.MetricsTable
import org.scalatest.DoNotDiscover

@DoNotDiscover
class ComputedColumnIntegrationSpec extends CassandraIntegrationSpec {

  private val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()
    execute(MetricsTable.drop.ifExists.toCQL)
    execute(MetricsTable.create.toCQL)
  }

  "A computed column" should "be created as a real, queryable column" in {
    // insertFrom fills the computed `shard` (name.length = 5) automatically
    execute(MetricsTable.insertFrom(Metric(id, "alice", 1.5)).toCQL)

    // and the row is retrievable only by constraining the whole key, shard included
    val row = rows(
      MetricsTable
        .select()
        .where(
          MetricsTable.shard === 5 and MetricsTable.id === id and MetricsTable.name === "alice"
        )
        .execute
    ).head

    row.getUuid("id") shouldBe id
    row.getString("name") shouldBe "alice"
    row.getDouble("value") shouldBe 1.5
  }

  it should "store the computed value so it can be read back explicitly" in {
    execute(MetricsTable.insertFrom(Metric(id, "bob", 2.0)).toCQL)

    val row = rows(
      MetricsTable
        .select(MetricsTable.id, MetricsTable.name, MetricsTable.shard)
        .where(MetricsTable.shard === 3 and MetricsTable.id === id)
        .execute
    ).head

    row.getInt("shard") shouldBe 3 // "bob".length
  }
}
