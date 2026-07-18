/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package integration

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.SensorsTable
import net.nmoncho.helenus.api.cql.UsersTable
import net.nmoncho.helenus.api.cql.dml.?
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover

@DoNotDiscover
class DeleteIntegrationSpec extends CassandraIntegrationSpec with BeforeAndAfterEach {

  private val id       = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val deviceId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()
    Seq(
      UsersTable.drop.ifExists.toCQL,
      UsersTable.create.toCQL,
      SensorsTable.drop.ifExists.toCQL,
      SensorsTable.create.toCQL
    ).foreach(execute)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    execute("TRUNCATE my_keyspace.users")
    execute("TRUNCATE iot.sensor_readings")
    execute(
      s"INSERT INTO my_keyspace.users (id, username, age, email) VALUES ($id, 'alice', 30, 'a@x.io')"
    )
    execute(
      s"INSERT INTO my_keyspace.users (id, username, age, email) VALUES ($id, 'bob', 25, 'b@x.io')"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 50, 1.0)"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 150, 2.0)"
    )
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 250, 3.0)"
    )
  }

  private def userRows() = rows(UsersTable.select().where(UsersTable.id === id).execute())

  "Delete.execute()" should "delete a single row" in {
    val delete = UsersTable.delete.where(UsersTable.id === id and UsersTable.username === "alice")
    println(delete.innerToCQL())
    println(delete.innerToCQL(prepared = true))
    delete.execute()

    userRows().map(_.getString("username")) shouldBe List("bob")
  }

  it should "delete a whole partition" in {
    UsersTable.delete.where(UsersTable.id === id).execute()

    userRows() shouldBe empty
  }

  it should "perform a range delete on the clustering column after the prefix" in {
    SensorsTable.delete
      .where(
        SensorsTable.deviceId === deviceId and SensorsTable.year === 2026 and SensorsTable.ts > 100L
      )
      .execute()

    val remaining = rows(
      SensorsTable
        .select()
        .where(SensorsTable.deviceId === deviceId and SensorsTable.year === 2026)
        .execute()
    )
    remaining.map(_.getLong("ts")) shouldBe List(50L)
  }

  it should "delete a single column and keep the row" in {
    UsersTable.delete
      .column(UsersTable.email)
      .where(UsersTable.id === id and UsersTable.username === "alice")
      .execute()

    val alice = rows(
      UsersTable.select().where(UsersTable.id === id and UsersTable.username === "alice").execute()
    ).head
    alice.isNull("email") shouldBe true
    alice.getInt("age") shouldBe 30
  }

  it should "report unapplied IF EXISTS on a missing row" in {
    val result = UsersTable.delete
      .where(UsersTable.id === id and UsersTable.username === "nobody")
      .ifExists
      .execute()

    result.wasApplied() shouldBe false
  }

  it should "not delete anything with an older USING TIMESTAMP" in {
    // The seed rows were written at server-now microseconds; timestamp 1000 is older.
    UsersTable.delete
      .usingTimestamp(Duration.of(1000, ChronoUnit.MICROS))
      .where(UsersTable.id === id and UsersTable.username === "alice")
      .execute()

    userRows().map(_.getString("username")).toSet shouldBe Set("alice", "bob")
  }

  it should "delete through a function produced from bound key parameters" in {
    val deleteUser = UsersTable.delete
      .where(UsersTable.id === id and UsersTable.username === ?)
      .toFunction

    deleteUser("alice")
    userRows().map(_.getString("username")) shouldBe List("bob")

    deleteUser("bob")
    userRows() shouldBe empty
  }

  // ---- shapes the gate rejects, confirmed rejected by the server ----------

  it should "be rejected by Cassandra when a non-key column is constrained" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.delete.where(UsersTable.age > 25).innerToCQL())
  }

  it should "be rejected by Cassandra when the partition key is partial" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.delete.where(UsersTable.username === "alice").innerToCQL())
  }

  it should "be rejected by Cassandra for a column-level delete without the full primary key" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.delete.column(UsersTable.email).where(UsersTable.id === id).innerToCQL())
  }
}
