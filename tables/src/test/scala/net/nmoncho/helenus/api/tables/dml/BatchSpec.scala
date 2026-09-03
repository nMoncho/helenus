/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BatchStatement
import net.nmoncho.helenus.api.tables.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `Batch` rendering and typing: grouping INSERT / UPDATE / DELETE into a CQL
  * `BATCH` with `.and`, the batch types, `USING TIMESTAMP`, and how the `?`
  * bind-marker types accumulate across statements.
  */
class BatchSpec extends AnyFlatSpec with Matchers {

  // Only used by the compile-time gate checks below; never executed.
  implicit val session: CqlSession = null

  "Batch" should "render the example: a LOGGED batch of an INSERT and an UPDATE" in {
    val batch = Batch(
      UsersTable.insert
        .value(UsersTable.id := fixedId)
        .value(UsersTable.username := ?)
        .value(UsersTable.age := 30)
    ).and(
      SensorsTable.update
        .set(SensorsTable.reading := 1.0)
        .where(
          SensorsTable.deviceId === fixedId and SensorsTable.year === ? and SensorsTable.ts === 100L
        )
    )

    batch.toCQL shouldBe
    "BEGIN BATCH\n" +
    s"  INSERT INTO my_keyspace.users (id, username, age) VALUES ($fixedId, ?, 30);\n" +
    s"  UPDATE iot.sensor_readings SET reading = 1.0 WHERE device_id = $fixedId " +
    "AND year = ? AND ts = 100;\n" +
    "APPLY BATCH"
  }

  it should "render an all-literal batch (no ? markers)" in {
    Batch(
      UsersTable.insert
        .value(UsersTable.id := fixedId)
        .value(UsersTable.username := "bob")
        .value(UsersTable.age := 40)
    ).toCQL shouldBe
    "BEGIN BATCH\n" +
    s"  INSERT INTO my_keyspace.users (id, username, age) VALUES ($fixedId, 'bob', 40);\n" +
    "APPLY BATCH"
  }

  it should "render an UNLOGGED batch" in {
    Batch
      .unlogged(UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a"))
      .toCQL should startWith("BEGIN UNLOGGED BATCH\n")
  }

  it should "render a COUNTER batch" in {
    Batch
      .counter(UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a"))
      .toCQL should startWith("BEGIN COUNTER BATCH\n")
  }

  it should "switch batch type fluently" in {
    Batch(
      UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a")
    ).unlogged.toCQL should
    startWith("BEGIN UNLOGGED BATCH")
  }

  it should "render USING TIMESTAMP right after BATCH" in {
    Batch(UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a"))
      .usingTimestamp(Duration.ofNanos(1234000L)) // 1234 micros
      .toCQL should startWith("BEGIN BATCH USING TIMESTAMP 1234\n")
  }

  it should "batch a DELETE alongside other statements" in {
    val cql = Batch(
      UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a")
    ).and(
      SensorsTable.delete.where(
        SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts === 1L
      )
    ).toCQL

    cql should include("INSERT INTO my_keyspace.users")
    cql should include(
      s"DELETE FROM iot.sensor_readings WHERE device_id = $fixedId AND year = 2026 AND ts = 1;"
    )
  }

  // ---- compile-time typing of the accumulated ? markers ---------------------

  "execute" should "compile for an all-literal batch (no ? markers)" in {
    assertCompiles(
      """Batch(UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "a"))
           .and(SensorsTable.delete.where(
             SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts === 1L))
           .execute()"""
    )
  }

  it should "NOT compile when any statement has an unbound ? marker" in {
    assertTypeError(
      """Batch(UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := ?))
           .execute()"""
    )
  }

  "prepare" should "type the function by the combined ? markers, in writing order" in {
    // insert contributes String (username), update contributes Int (year)
    assertCompiles(
      """val f: (String, Int) => BatchStatement =
           Batch(UsersTable.insert
                   .value(UsersTable.id := fixedId)
                   .value(UsersTable.username := ?)
                   .value(UsersTable.age := 30))
             .and(SensorsTable.update
                   .set(SensorsTable.reading := 1.0)
                   .where(SensorsTable.deviceId === fixedId and SensorsTable.year === ? and SensorsTable.ts === 100L))
             .prepare
         f: Any"""
    )
  }

  it should "reject a function of the wrong arity" in {
    assertTypeError(
      """val f: String => BatchStatement =
           Batch(UsersTable.insert
                   .value(UsersTable.id := fixedId)
                   .value(UsersTable.username := ?))
             .and(SensorsTable.update
                   .set(SensorsTable.reading := 1.0)
                   .where(SensorsTable.deviceId === fixedId and SensorsTable.year === ? and SensorsTable.ts === 100L))
             .prepare"""
    )
  }
}
