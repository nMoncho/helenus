/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables
package dml

import net.nmoncho.helenus.api.cql.tables.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** The compile-time execute() gate on SELECT. */
class SelectValidSpec extends AnyFlatSpec with Matchers {
  import net.nmoncho.helenus._
  // ---- positive: equality-only shapes ------------------------------------

  "Select.execute()" should "be available when there is no WHERE clause at all" in {
    UsersTable.select().toCQL shouldBe
    "SELECT * FROM my_keyspace.users"
  }

  it should "be available once the full partition key is === constrained" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId)
      .toCQL

    cql shouldBe s"SELECT * FROM my_keyspace.users WHERE id = $fixedId"
  }

  it should "accept a composite partition key constrained out of order" in {
    val cql = EventsTable
      .select()
      .where(EventsTable.eventType === "click" and EventsTable.tenantId === "acme")
      .toCQL

    cql should include("WHERE tenant_id = 'acme' AND event_type = 'click'")
  }

  it should "accept primary-key predicates in any order and reorder them to CQL order" in {
    val cql = SensorsTable
      .select()
      .where(
        SensorsTable.ts > 100L and
          SensorsTable.year === 2026 and
          SensorsTable.deviceId === fixedId
      )
      .toCQL

    cql shouldBe
    s"SELECT * FROM iot.sensor_readings WHERE device_id = $fixedId AND year = 2026 AND ts > 100"
  }

  it should "accept a fully === constrained primary key" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.username === "alice" and UsersTable.id === fixedId)
      .toCQL

    cql should include(s"WHERE id = $fixedId AND username = 'alice'")
  }

  it should "accept a range slice on the clustering column following the === prefix" in {
    val cql = SensorsTable
      .select()
      .where(
        SensorsTable.deviceId === fixedId and
          SensorsTable.year === 2026 and
          SensorsTable.ts > 100L and SensorsTable.ts <= 200L
      )
      .toCQL

    cql should include("AND ts > 100 AND ts <= 200")
  }

  // ---- positive: IN shapes ------------------------------------------------

  it should "accept IN on a single-column partition key" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id.in(Seq(fixedId)))
      .toCQL

    cql should include(s"WHERE id IN ($fixedId)")
  }

  it should "accept IN on the last column of a composite partition key" in {
    val cql = EventsTable
      .select()
      .where(EventsTable.tenantId === "acme" and EventsTable.eventType.in(Seq("click", "view")))
      .toCQL

    cql should include("WHERE tenant_id = 'acme' AND event_type IN ('click', 'view')")
  }

  it should "accept partition IN combined with clustering restrictions and a range" in {
    val cql = SensorsTable
      .select()
      .where(
        SensorsTable.deviceId
          .in(Seq(fixedId)) and SensorsTable.year === 2026 and SensorsTable.ts > 100L
      )
      .toCQL

    cql should include(s"WHERE device_id IN ($fixedId) AND year = 2026 AND ts > 100")
  }

  it should "accept IN on the last clustering column when the rest of the key is ===" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId and UsersTable.username.in(Seq("alice", "bob")))
      .toCQL

    cql should include(s"WHERE id = $fixedId AND username IN ('alice', 'bob')")
  }

  it should "accept IN on the last of several clustering columns" in {
    val cql = SensorsTable
      .select()
      .where(
        SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts
          .in(Seq(100L, 200L))
      )
      .toCQL

    cql should include("AND ts IN (100, 200)")
  }

  it should "always be available after allowFiltering, even with misplaced IN" in {
    val cql = UsersTable
      .select(UsersTable.id)
      .where(UsersTable.email.in(Seq("a@b.c")))
      .allowFiltering
      .toCQL

    cql should endWith("ALLOW FILTERING")
  }

  // ---- negative: equality shapes ------------------------------------------

  it should "NOT compile when a non-key column is constrained" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.age > 25).toCQL"""
    )
  }

  it should "NOT compile when a non-key column is constrained even alongside the full primary key" in {
    assertTypeError(
      """EventsTable.select()
           .where(EventsTable.tenantId === "acme" and EventsTable.eventType === "click" and EventsTable.payload === "x")
           .toCQL"""
    )
  }

  it should "NOT compile when only part of a composite partition key is constrained" in {
    assertTypeError(
      """EventsTable.select().where(EventsTable.tenantId === "acme").toCQL"""
    )
  }

  it should "NOT compile when only clustering columns are constrained" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.username === "alice").toCQL"""
    )
  }

  it should "NOT compile when a clustering column is skipped" in {
    assertTypeError(
      """SensorsTable.select()
           .where(SensorsTable.deviceId === fixedId and SensorsTable.ts > 100L)
           .toCQL"""
    )
  }

  it should "NOT compile when an equality follows a range across clustering columns" in {
    assertTypeError(
      """SensorsTable.select()
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year > 2020 and SensorsTable.ts === 100L)
           .toCQL"""
    )
  }

  it should "NOT compile when ranges span two clustering columns" in {
    assertTypeError(
      """SensorsTable.select()
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year > 2020 and SensorsTable.ts > 100L)
           .toCQL"""
    )
  }

  // ---- negative: IN shapes -------------------------------------------------

  it should "NOT compile with IN on a non-last partition-key column" in {
    assertTypeError(
      """EventsTable.select()
           .where(EventsTable.tenantId.in(Seq("acme")) and EventsTable.eventType === "click")
           .toCQL"""
    )
  }

  it should "NOT compile with IN on a non-last clustering column" in {
    assertTypeError(
      """SensorsTable.select()
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year.in(Seq(2026)))
           .toCQL"""
    )
  }

  it should "NOT compile with IN on the last clustering column combined with a range" in {
    assertTypeError(
      """SensorsTable.select()
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year > 2020 and SensorsTable.ts.in(Seq(100L)))
           .toCQL"""
    )
  }

  it should "NOT compile with IN on a non-key column" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.email.in(Seq("a@b.c"))).toCQL"""
    )
  }

  it should "NOT compile with === and IN on the same column" in {
    assertTypeError(
      """UsersTable.select()
           .where(UsersTable.id === fixedId and UsersTable.id.in(Seq(fixedId)))
           .toCQL"""
    )
  }
}
