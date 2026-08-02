/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration
import java.time.temporal.ChronoUnit

import net.nmoncho.helenus.api.tables.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InsertSpec extends AnyFlatSpec with Matchers {

  "Insert" should "generate a basic INSERT statement" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "alice")
      .value(UsersTable.age := 30)
      .toCQL

    cql shouldBe
    "INSERT INTO my_keyspace.users (id, username, age) " +
    s"VALUES ($fixedId, 'alice', 30)"
  }

  it should "escape single quotes in string values" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "o'reilly")
      .toCQL

    cql should include("'o''reilly'")
  }

  it should "support IF NOT EXISTS" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "alice")
      .ifNotExists
      .toCQL

    cql should include("IF NOT EXISTS")
  }

  it should "support USING TTL" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "alice")
      .usingTTL(Duration.of(3600, ChronoUnit.SECONDS))
      .toCQL

    cql should endWith("USING TTL 3600")
  }

  it should "support USING TIMESTAMP" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "alice")
      .usingTimestamp(Duration.of(1_000_000L, ChronoUnit.MICROS))
      .toCQL

    cql should endWith("USING TIMESTAMP 1000000")
  }

  it should "support collection values" in {
    val cql = UsersTable.insert
      .value(UsersTable.id := fixedId)
      .value(UsersTable.username := "alice")
      .value(UsersTable.tags := Set("admin", "beta"))
      .toCQL

    cql should include("tags")
    cql should include("'admin'")
  }

  it should "NOT compile execute/prepare unless the whole primary key is set" in {
    // An implicit session is in scope so the only thing missing is the
    // `CanInsert` evidence (the primary key), not the `CqlSession`.
    // UsersTable's primary key is `id` (partition) + `username` (clustering).
    implicit val session: com.datastax.oss.driver.api.core.CqlSession = null

    // no columns at all
    assertTypeError("UsersTable.insert.execute()")
    assertTypeError("UsersTable.insert.prepare")

    // partial key: `id` set but the `username` clustering column is missing
    assertTypeError("UsersTable.insert.value(UsersTable.id := fixedId).execute()")

    // whole primary key set
    assertCompiles(
      """UsersTable.insert.value(UsersTable.id := fixedId).value(UsersTable.username := "alice").execute()"""
    )

    // whole primary key plus an optional non-key column is also fine
    assertCompiles(
      """UsersTable.insert
           .value(UsersTable.id := fixedId)
           .value(UsersTable.username := "alice")
           .value(UsersTable.age := 30)
           .execute()"""
    )
  }

  it should "NOT compile with a value from another table" in {
    assertTypeError(
      """UsersTable.insert.value(EventsTable.payload := "x")"""
    )
  }
}
