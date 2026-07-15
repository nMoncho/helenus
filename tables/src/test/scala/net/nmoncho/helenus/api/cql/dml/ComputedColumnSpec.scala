/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import net.nmoncho.helenus.api.cql.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ComputedColumnSpec extends AnyFlatSpec with Matchers {

  "A computed column" should "appear in CREATE TABLE after the case-class columns" in {
    val c = MetricsTable.create.toCQL

    c shouldBe
    "CREATE TABLE monitoring.metrics (id uuid, name text, value double, shard int, " +
    "PRIMARY KEY ((shard, id), name))"
  }

  it should "be usable as part of the primary key in the execute gate" in {
    val cql = MetricsTable
      .select()
      .where(
        MetricsTable.shard === 5 and MetricsTable.id === fixedId and MetricsTable.name === "cpu"
      )
      .execute()
    cql should include("WHERE shard = 5 AND id = " + fixedId + " AND name = 'cpu'")
  }

  it should "be projectable explicitly" in {
    val cql = MetricsTable
      .select(MetricsTable.id, MetricsTable.shard)
      .where(MetricsTable.shard === 5 and MetricsTable.id === fixedId)
      .toCQL
    cql should startWith("SELECT id, shard FROM")
  }

  it should "NOT let the gate pass without the computed key column constrained" in {
    assertTypeError(
      """MetricsTable.select().where(MetricsTable.id === fixedId).execute()"""
    )
  }

  "insertFrom" should "write every field plus the computed column" in {
    val cql = MetricsTable.insertFrom(Metric(fixedId, "alice", 1.5)).toCQL
    cql shouldBe
    s"INSERT INTO monitoring.metrics (id, name, value, shard) VALUES ($fixedId, 'alice', 1.5, 5)"
  }

  it should "still support chaining after insertFrom" in {
    val cql = MetricsTable.insertFrom(Metric(fixedId, "bob", 2.0)).ifNotExists.usingTTL(60).toCQL
    cql should endWith("IF NOT EXISTS USING TTL 60")
    cql should include("shard) VALUES")
  }

  it should "allow a computed column to be set explicitly too" in {
    val cql = MetricsTable.insert
      .value(MetricsTable.id := fixedId)
      .value(MetricsTable.shard := 9)
      .toCQL
    cql should include("(id, shard) VALUES")
    cql should include(s"($fixedId, 9)")
  }
}
