/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.integration

import java.util.UUID

import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.EventsTable
import net.nmoncho.helenus.api.cql.SensorsTable
import net.nmoncho.helenus.api.cql.UsersTable
import net.nmoncho.helenus.api.cql.dml.?
import org.scalatest.DoNotDiscover

/** Every statement the SELECT gate admits is executed against the embedded
  * Cassandra; every shape the gate rejects at compile time is executed as a
  * raw string and must be rejected by the server too. Together they show the
  * gate matches real CQL semantics.
  */
@DoNotDiscover
class SelectExecuteIntegrationSpec extends CassandraIntegrationSpec {

  private val userA    = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val userB    = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
  private val deviceId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
  private val eventId  = UUID.fromString("423e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()
    Seq(
      UsersTable.drop.ifExists.toCQL,
      UsersTable.create.toCQL,
      EventsTable.drop.ifExists.toCQL,
      EventsTable.create.toCQL,
      SensorsTable.drop.ifExists.toCQL,
      SensorsTable.create.toCQL
    ).foreach(execute)

    // users: two rows in partition A, one in partition B
    execute(s"INSERT INTO my_keyspace.users (id, username, age) VALUES ($userA, 'alice', 30)")
    execute(s"INSERT INTO my_keyspace.users (id, username, age) VALUES ($userA, 'bob', 25)")
    execute(s"INSERT INTO my_keyspace.users (id, username, age) VALUES ($userB, 'carol', 41)")

    // events: two types for acme, one for globex
    execute(
      s"INSERT INTO analytics.events (tenant_id, event_type, event_id, payload) VALUES ('acme', 'click', $eventId, 'p1')"
    )
    execute(
      s"INSERT INTO analytics.events (tenant_id, event_type, event_id, payload) VALUES ('acme', 'view', $eventId, 'p2')"
    )
    execute(
      s"INSERT INTO analytics.events (tenant_id, event_type, event_id, payload) VALUES ('globex', 'click', $eventId, 'p3')"
    )

    // sensors: three readings in 2026, one in 2025
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 50, 1.0)"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 150, 2.0)"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 250, 3.0)"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2025, 100, 0.5)"
    )
  }

  // ---- shapes the gate admits, verified against real data -----------------

  "Select.execute()" should "run an unrestricted query" in {
    rows(UsersTable.select().execute()) should have size 3
  }

  it should "return one partition when the partition key is constrained" in {
    rows(UsersTable.select().where(UsersTable.id === userA).execute()) should have size 2
  }

  it should "return a single row for a fully constrained primary key" in {
    val result = rows(
      UsersTable
        .select()
        .where(UsersTable.username === "alice" and UsersTable.id === userA)
        .execute()
    )
    result should have size 1
    result.head.getInt("age") shouldBe 30
  }

  it should "run a composite partition key constrained out of order" in {
    val result = rows(
      EventsTable
        .select()
        .where(EventsTable.eventType === "click" and EventsTable.tenantId === "acme")
        .execute()
    )
    result should have size 1
    result.head.getString("payload") shouldBe "p1"
  }

  it should "run a clustering range after the === prefix" in {
    val result = rows(
      SensorsTable
        .select()
        .where(
          SensorsTable.ts > 100L and SensorsTable.year === 2026 and SensorsTable.deviceId === deviceId
        )
        .execute()
    )
    result.map(_.getLong("ts")).toSet shouldBe Set(150L, 250L)
  }

  it should "run a slice with two ranges on the same clustering column" in {
    val result = rows(
      SensorsTable
        .select()
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year === 2026 and SensorsTable.ts > 100L and SensorsTable.ts <= 250L
        )
        .execute()
    )
    result.map(_.getLong("ts")).toSet shouldBe Set(150L, 250L)
  }

  it should "run IN on a single-column partition key" in {
    rows(
      UsersTable.select().where(UsersTable.id.in(Seq(userA, userB))).execute()
    ) should have size 3
  }

  it should "run IN on the last column of a composite partition key" in {
    val result = rows(
      EventsTable
        .select()
        .where(EventsTable.tenantId === "acme" and EventsTable.eventType.in(Seq("click", "view")))
        .execute()
    )
    result.map(_.getString("payload")).toSet shouldBe Set("p1", "p2")
  }

  it should "run partition IN combined with clustering restrictions and a range" in {
    val result = rows(
      SensorsTable
        .select()
        .where(
          SensorsTable.deviceId
            .in(Seq(deviceId)) and SensorsTable.year === 2026 and SensorsTable.ts > 100L
        )
        .execute()
    )
    result.map(_.getLong("ts")).toSet shouldBe Set(150L, 250L)
  }

  it should "run IN on the last clustering column" in {
    // Cassandra 3.11 restriction the type gate does not model: clustering IN
    // is rejected when a collection column is part of the projection, so the
    // select must name non-collection columns here.
    val result = rows(
      UsersTable
        .select(UsersTable.id, UsersTable.username, UsersTable.age)
        .where(UsersTable.id === userA and UsersTable.username.in(Seq("alice", "bob")))
        .execute()
    )
    result.map(_.getString("username")).toSet shouldBe Set("alice", "bob")
  }

  it should "respect the DESC clustering order declared in CK" in {
    val result = rows(
      SensorsTable
        .select()
        .where(SensorsTable.deviceId === deviceId and SensorsTable.year === 2026)
        .execute()
    )
    result.map(_.getLong("ts")) shouldBe List(250L, 150L, 50L)
  }

  it should "apply query-time ORDER BY" in {
    val result = rows(
      UsersTable
        .select()
        .where(UsersTable.id === userA)
        .orderBy(UsersTable.username.desc)
        .execute()
    )
    result.map(_.getString("username")) shouldBe List("bob", "alice")
  }

  it should "apply LIMIT" in {
    rows(UsersTable.select().limit(2).execute()) should have size 2
  }

  it should "run non-key filters through allowFiltering" in {
    val result = rows(UsersTable.select().where(UsersTable.age > 26).allowFiltering.execute())
    result.map(_.getString("username")).toSet shouldBe Set("alice", "carol")
  }

  // ---- bind markers: produced functions run against the server -------------

  it should "run a function produced from bound key parameters" in {
    val fn = UsersTable
      .select(UsersTable.id, UsersTable.username, UsersTable.age)
      .where(UsersTable.id === ?)
      .toFunction

    rows(fn(userA)) should have size 2
    rows(fn(userB)) should have size 1
  }

  it should "run the same function with different arguments" in {
    val fn = UsersTable
      .select(UsersTable.id, UsersTable.username, UsersTable.age)
      .where(UsersTable.id === ? and UsersTable.username === ?)
      .toFunction

    rows(fn(userA, "alice")).head.getInt("age") shouldBe 30
    rows(fn(userA, "bob")).head.getInt("age") shouldBe 25
  }

  it should "run a bound non-key range through allowFiltering" in {
    val fn = UsersTable
      .select(UsersTable.id, UsersTable.username, UsersTable.age)
      .where(UsersTable.age >= ? and UsersTable.age <= ?)
      .allowFiltering
      .toFunction

    rows(fn(26, 50)).map(_.getString("username")).toSet shouldBe Set("alice", "carol")
  }

  // ---- shapes the gate rejects, confirmed rejected by the server ----------
  // These strings can only be produced via toCQL, never via execute; running
  // them raw shows the compile-time gate matches server behavior.

  it should "be rejected by Cassandra for a non-key filter without ALLOW FILTERING" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.select().where(UsersTable.age > 25).toCQL)
  }

  it should "be rejected by Cassandra for a partial partition key" in {
    an[InvalidQueryException] should be thrownBy
    execute(EventsTable.select().where(EventsTable.tenantId === "acme").toCQL)
  }

  it should "be rejected by Cassandra for clustering-only restrictions" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.select().where(UsersTable.username === "alice").toCQL)
  }

  it should "be rejected by Cassandra when a clustering column is skipped" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      SensorsTable
        .select()
        .where(SensorsTable.deviceId === deviceId and SensorsTable.ts > 100L)
        .toCQL
    )
  }

  it should "be rejected by Cassandra for ranges on two clustering columns" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      SensorsTable
        .select()
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year > 2020 and SensorsTable.ts > 100L
        )
        .toCQL
    )
  }
}
