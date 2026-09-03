/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package integration

import java.util.UUID

import net.nmoncho.helenus.api.tables.dml.?
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover

/** `Batch` executed against a real (embedded) Cassandra: a LOGGED batch applies
  * every statement, a batch can span more than one table, UNLOGGED batches run,
  * and a prepared batch binds its `?` markers across statements in writing
  * order (each statement in turn, its assignments before its predicates).
  */
@DoNotDiscover
class BatchIntegrationSpec extends CassandraIntegrationSpec with BeforeAndAfterEach {

  import net.nmoncho.helenus._

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
    // A sensor row for the UPDATE statements to modify.
    execute(
      s"INSERT INTO iot.sensor_readings (device_id, year, ts, reading) VALUES ($deviceId, 2026, 100, 0.0)"
    )
  }

  private def userAge(username: String): Option[Int] =
    rows(
      UsersTable
        .select(UsersTable.age)
        .where(UsersTable.id === id and UsersTable.username === username)
        .execute()
    ).headOption

  private def readingAt(ts: Long): Option[Double] =
    rows(
      SensorsTable
        .select(SensorsTable.reading)
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year === 2026 and SensorsTable.ts === ts
        )
        .execute()
    ).headOption

  "Batch.execute()" should "apply every statement of an all-literal LOGGED batch" in {
    Batch(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
    ).and(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "bob")
        .value(UsersTable.age := 25)
    ).execute()

    userAge("alice") shouldBe Some(30)
    userAge("bob") shouldBe Some(25)
  }

  it should "span more than one table in a single batch" in {
    Batch(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "carol")
        .value(UsersTable.age := 40)
    ).and(
      SensorsTable.update
        .set(SensorsTable.reading := 1.5)
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year === 2026 and SensorsTable.ts === 100L
        )
    ).execute()

    userAge("carol") shouldBe Some(40)
    readingAt(100L) shouldBe Some(1.5)
  }

  it should "run an UNLOGGED batch" in {
    Batch
      .unlogged(
        UsersTable.insert
          .value(UsersTable.id := id)
          .value(UsersTable.username := "dave")
          .value(UsersTable.age := 22)
      )
      .execute()

    userAge("dave") shouldBe Some(22)
  }

  "Batch.prepare" should "bind ? markers across statements in writing order (the example)" in {
    val run = Batch(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := ?)
        .value(UsersTable.age := 30)
    ).and(
      SensorsTable.update
        .set(SensorsTable.reading := 1.0)
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year === ? and SensorsTable.ts === 100L
        )
    ).prepare

    // First the insert's marker (username: String), then the update's (year: Int).
    session.execute(run("alice", 2026))

    userAge("alice") shouldBe Some(30)
    readingAt(100L) shouldBe Some(1.0)
  }

  it should "reuse a prepared batch across argument sets" in {
    val run = Batch(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := ?)
        .value(UsersTable.age := ?)
    ).prepare

    session.execute(run("frank", 51))
    session.execute(run("grace", 52))

    userAge("frank") shouldBe Some(51)
    userAge("grace") shouldBe Some(52)
  }
}
