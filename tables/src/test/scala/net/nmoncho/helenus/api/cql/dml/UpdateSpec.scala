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

class UpdateSpec extends AnyFlatSpec with Matchers {

  // ---- statement building --------------------------------------------------

  "Update" should "generate a basic UPDATE statement" in {
    val cql = UsersTable.update
      .set(UsersTable.age := 31)
      .set(UsersTable.email := "alice@example.com")
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .toCQL

    cql shouldBe
    "UPDATE my_keyspace.users SET age = 31, email = 'alice@example.com' " +
    s"WHERE id = $fixedId AND username = 'alice'"
  }

  it should "support USING TTL" in {
    val cql = UsersTable.update
      .usingTTL(7200)
      .set(UsersTable.age := 31)
      .where(UsersTable.id === fixedId)
      .toCQL

    cql should include("USING TTL 7200")
  }

  it should "support IF EXISTS" in {
    val cql = UsersTable.update
      .set(UsersTable.age := 31)
      .where(UsersTable.id === fixedId)
      .ifExists
      .toCQL

    cql should endWith("IF EXISTS")
  }

  it should "require at least one SET assignment" in {
    an[IllegalArgumentException] should be thrownBy
    UsersTable.update.where(UsersTable.id === fixedId).toCQL
  }

  it should "NOT provide an and method on Update" in {
    assertTypeError(
      """UsersTable.update.set(UsersTable.age := 31).where(UsersTable.id === fixedId).and(UsersTable.username === "alice")"""
    )
  }

  it should "NOT compile a set with an assignment from another table" in {
    assertTypeError(
      """UsersTable.update.set(EventsTable.payload := "x")"""
    )
  }

  // ---- execute gate: positive ----------------------------------------------

  "Update.execute" should "be available when the entire primary key is === constrained" in {
    val cql = UsersTable.update
      .set(UsersTable.age := 31)
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .execute

    cql shouldBe s"UPDATE my_keyspace.users SET age = 31 WHERE id = $fixedId AND username = 'alice'"
  }

  it should "accept the primary key constrained in any order" in {
    val cql = EventsTable.update
      .set(EventsTable.payload := "p")
      .where(
        EventsTable.eventId === fixedId and EventsTable.eventType === "click" and EventsTable.tenantId === "acme"
      )
      .execute

    cql should include("WHERE event_id = ")
  }

  it should "accept IN on the last primary-key component" in {
    val cql = UsersTable.update
      .set(UsersTable.age := 31)
      .where(UsersTable.id === fixedId and UsersTable.username.in(Seq("alice", "bob")))
      .execute

    cql shouldBe
    "UPDATE my_keyspace.users SET age = 31 " +
    s"WHERE id = $fixedId AND username IN ('alice', 'bob')"
  }

  it should "accept IN on the last clustering column of a composite key" in {
    val cql = EventsTable.update
      .set(EventsTable.payload := "p")
      .where(
        EventsTable.tenantId === "acme" and EventsTable.eventType === "click" and EventsTable.eventId
          .in(Seq(fixedId))
      )
      .execute

    cql should include(s"event_id IN ($fixedId)")
  }

  // ---- execute gate: negative ------------------------------------------------

  it should "NOT compile without a WHERE clause" in {
    assertTypeError("""UsersTable.update.set(UsersTable.age := 31).execute""")
  }

  it should "NOT compile when clustering columns are missing" in {
    assertTypeError(
      """UsersTable.update.set(UsersTable.age := 31).where(UsersTable.id === fixedId).execute"""
    )
  }

  it should "NOT compile with a range predicate" in {
    assertTypeError(
      """SensorsTable.update
           .set(SensorsTable.reading := 1.0)
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts > 100L)
           .execute"""
    )
  }

  it should "NOT compile when a non-key column is constrained alongside the full primary key" in {
    assertTypeError(
      """UsersTable.update
           .set(UsersTable.age := 31)
           .where(UsersTable.id === fixedId and UsersTable.username === "alice" and UsersTable.email === "a@b.c")
           .execute"""
    )
  }

  it should "NOT compile with IN on a non-last primary-key component" in {
    assertTypeError(
      """UsersTable.update
           .set(UsersTable.age := 31)
           .where(UsersTable.id.in(Seq(fixedId)) and UsersTable.username === "alice")
           .execute"""
    )
  }

  it should "NOT compile with IN when the rest of the primary key is incomplete" in {
    assertTypeError(
      """UsersTable.update
           .set(UsersTable.age := 31)
           .where(UsersTable.username.in(Seq("alice")))
           .execute"""
    )
  }

  it should "NOT compile with === and IN on the same column" in {
    assertTypeError(
      """UsersTable.update
           .set(UsersTable.age := 31)
           .where(UsersTable.id === fixedId and UsersTable.username === "alice" and UsersTable.username.in(Seq("bob")))
           .execute"""
    )
  }

  it should "NOT compile with IN on a non-key column" in {
    assertTypeError(
      """UsersTable.update
           .set(UsersTable.age := 31)
           .where(UsersTable.id === fixedId and UsersTable.username === "alice" and UsersTable.email.in(Seq("a@b.c")))
           .execute"""
    )
  }
}
