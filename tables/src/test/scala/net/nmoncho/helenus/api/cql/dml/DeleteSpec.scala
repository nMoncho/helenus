/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import java.time.Duration
import java.time.temporal.ChronoUnit

import net.nmoncho.helenus.api.cql.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeleteSpec extends AnyFlatSpec with Matchers {

  // ---- statement building --------------------------------------------------

  "Delete" should "generate a full row DELETE" in {
    val cql = UsersTable.delete
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .toCQL

    cql shouldBe s"DELETE FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
  }

  it should "generate a column-level DELETE" in {
    val cql = UsersTable.delete
      .column(UsersTable.email)
      .column(UsersTable.tags)
      .where(UsersTable.id === fixedId)
      .toCQL

    cql should startWith("DELETE email, tags FROM")
  }

  it should "support IF EXISTS" in {
    val cql = UsersTable.delete
      .where(UsersTable.id === fixedId)
      .ifExists
      .toCQL

    cql should endWith("IF EXISTS")
  }

  it should "support USING TIMESTAMP" in {
    val cql = UsersTable.delete
      .usingTimestamp(Duration.of(9_999_999L, ChronoUnit.MICROS))
      .where(UsersTable.id === fixedId)
      .toCQL

    cql should include("USING TIMESTAMP 9999999")
  }

  it should "NOT provide an and method on Delete" in {
    assertTypeError(
      """UsersTable.delete.where(UsersTable.id === fixedId).and(UsersTable.username === "alice")"""
    )
  }

  it should "NOT compile a column drop from another table" in {
    assertTypeError(
      """UsersTable.delete.column(EventsTable.payload)"""
    )
  }

  // ---- execute() gate: positive ----------------------------------------------

  "Delete.execute()" should "be available for a whole-partition delete" in {
    UsersTable.delete.where(UsersTable.id === fixedId).execute() shouldBe
    s"DELETE FROM my_keyspace.users WHERE id = $fixedId"
  }

  it should "be available for a single-row delete" in {
    val cql = UsersTable.delete
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .execute()

    cql shouldBe s"DELETE FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
  }

  it should "be available for a range delete on the clustering column after the prefix" in {
    val cql = SensorsTable.delete
      .where(
        SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts > 100L
      )
      .execute()

    cql should include(s"WHERE device_id = $fixedId AND year = 2026 AND ts > 100")
  }

  it should "be available for a column-level delete when the entire primary key is constrained" in {
    val cql = UsersTable.delete
      .column(UsersTable.email)
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .execute()

    cql shouldBe s"DELETE email FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
  }

  // ---- execute() gate: negative ------------------------------------------------

  it should "NOT compile without a WHERE clause" in {
    assertTypeError("""UsersTable.delete.execute()""")
  }

  it should "NOT compile when a non-key column is constrained" in {
    assertTypeError("""UsersTable.delete.where(UsersTable.age > 25).execute()""")
  }

  it should "NOT compile when the partition key is only partially constrained" in {
    assertTypeError("""EventsTable.delete.where(EventsTable.tenantId === "acme").execute()""")
  }

  it should "NOT compile a range delete that skips a clustering column" in {
    assertTypeError(
      """SensorsTable.delete
           .where(SensorsTable.deviceId === fixedId and SensorsTable.ts > 100L)
           .execute()"""
    )
  }

  it should "NOT compile a column-level delete without the entire primary key" in {
    assertTypeError(
      """UsersTable.delete
           .column(UsersTable.email)
           .where(UsersTable.id === fixedId)
           .execute()"""
    )
  }

  it should "NOT compile a column-level delete with a range predicate" in {
    assertTypeError(
      """SensorsTable.delete
           .column(SensorsTable.reading)
           .where(SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts > 100L)
           .execute()"""
    )
  }

  it should "NOT compile with IN, even on the last primary-key component" in {
    assertTypeError(
      """UsersTable.delete
           .where(UsersTable.id === fixedId and UsersTable.username.in(Seq("alice")))
           .execute()"""
    )
  }

  it should "NOT compile a column-level delete with IN" in {
    assertTypeError(
      """UsersTable.delete
           .column(UsersTable.email)
           .where(UsersTable.id === fixedId and UsersTable.username.in(Seq("alice")))
           .execute()"""
    )
  }
}
