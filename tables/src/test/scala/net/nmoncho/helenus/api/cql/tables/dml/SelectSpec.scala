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

/** SELECT statement building (rendering-level, via toCQL). */
class SelectSpec extends AnyFlatSpec with Matchers {

  import net.nmoncho.helenus._

  "Select" should "generate a SELECT for specific columns" in {
    val query = UsersTable
      .select(UsersTable.id, UsersTable.username, UsersTable.age)
      .where(UsersTable.id === fixedId)
      .toCQL

    query shouldBe
    s"SELECT id, username, age FROM my_keyspace.users WHERE id = $fixedId"
  }

  it should "select all columns when none are given" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId)
      .toCQL

    cql should startWith("SELECT * FROM")
  }

  it should "support multiple WHERE predicates combined with and" in {
    val cql = UsersTable
      .select(UsersTable.age)
      .where(UsersTable.id === fixedId and UsersTable.username === "alice")
      .toCQL

    cql should include(s"WHERE id = $fixedId AND username = 'alice'")
  }

  it should "NOT support lambda-style predicates" in {
    assertTypeError(
      """UsersTable.select().where(_.id === fixedId)"""
    )
  }

  it should "support LIMIT" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId)
      .limit(10)
      .toCQL

    cql should endWith("LIMIT 10")
  }

  it should "support ORDER BY built from column sort methods" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId)
      .orderBy(UsersTable.username.desc)
      .toCQL

    cql should include("ORDER BY username DESC")
  }

  it should "support ORDER BY on several columns at once" in {
    val cql = SensorsTable
      .select()
      .where(SensorsTable.deviceId === fixedId)
      .orderBy(SensorsTable.year.asc, SensorsTable.ts.desc)
      .toCQL

    cql should include("ORDER BY year ASC, ts DESC")
  }

  it should "default ORDER BY to ascending for a bare column" in {
    val cql = UsersTable
      .select()
      .where(UsersTable.id === fixedId)
      .orderBy(UsersTable.username)
      .toCQL

    cql should include("ORDER BY username ASC")
  }

  it should "support ALLOW FILTERING" in {
    val cql = UsersTable
      .select(UsersTable.id)
      .where(UsersTable.age > 25)
      .allowFiltering
      .toCQL

    cql should endWith("ALLOW FILTERING")
  }

  it should "render IN predicates" in {
    val cql = UsersTable
      .select(UsersTable.id, UsersTable.username)
      .where(UsersTable.username.in(Seq("alice", "bob")))
      .allowFiltering
      .toCQL

    cql should include("username IN ('alice', 'bob')")
  }

  it should "support comparison operators" in {
    val cql = UsersTable
      .select(UsersTable.id, UsersTable.username)
      .where(UsersTable.age >= 18 and UsersTable.age <= 65)
      .allowFiltering
      .toCQL

    cql should include("age >= 18")
    cql should include("age <= 65")
  }

  it should "reorder predicates to CQL key order" in {
    val cql = SensorsTable
      .select()
      .where(
        SensorsTable.ts > 100L and SensorsTable.year === 2026 and SensorsTable.deviceId === fixedId
      )
      .toCQL

    cql should include(s"WHERE device_id = $fixedId AND year = 2026 AND ts > 100")
  }

  it should "NOT provide an and method on Select" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.id === fixedId).and(UsersTable.username === "alice")"""
    )
  }
}
