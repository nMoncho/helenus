/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** `Table.rowMapper`, built by `registerAllColumns` from the table's OWN
  * registered columns — not from an independent `LabelledGeneric` derivation
  * that could disagree with the table's `naming` scheme (see [[TestRow]] for
  * how these tests mock the driver `Row`).
  */
class RowMapperSpec extends AnyFlatSpec with Matchers {

  "Table.rowMapper" should "read every field by the table's actual registered CQL column name, honoring a non-default naming scheme" in {
    val id  = UUID.randomUUID()
    val row = TestRow(
      "device_id" -> id, // SensorsTable uses SnakeCase; the field is `deviceId`
      "year" -> 2026,
      "ts" -> 100L,
      "reading" -> 98.6
    )

    SensorsTable.rowMapper(row) shouldBe Sensors(id, 2026, 100L, 98.6)
  }

  it should "read fields in field-declaration order, independent of the order columns were passed to registerAllColumns" in {
    val id  = UUID.randomUUID()
    val row = TestRow(
      "id" -> id,
      "username" -> "alice",
      "age" -> 30,
      "email" -> "alice@example.com",
      "tags" -> Set("a", "b"),
      "metadata" -> Map("k" -> "v")
    )

    UsersTable.rowMapper(row) shouldBe User(
      id,
      "alice",
      30,
      "alice@example.com",
      Set("a", "b"),
      Map("k" -> "v")
    )
  }

  it should "use every field's actual registered name even when several fields are snake_cased" in {
    val eventId = UUID.randomUUID()
    val row     = TestRow(
      "tenant_id" -> "acme",
      "event_type" -> "click",
      "event_id" -> eventId,
      "payload" -> "{}"
    )

    EventsTable.rowMapper(row) shouldBe Event("acme", "click", eventId, "{}")
  }

  it should "ignore computed columns: they are not fields of A, so registerAllColumns never lists them" in {
    val id  = UUID.randomUUID()
    val row = TestRow("id" -> id, "name" -> "cpu.load", "value" -> 1.5)
    // shard is computed (derived from `name`), not read from the row at all

    MetricsTable.rowMapper(row) shouldBe Metric(id, "cpu.load", 1.5)
  }
}
