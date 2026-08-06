/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package integration

import net.nmoncho.helenus.api.tables.TestValues.fixedId
import net.nmoncho.helenus.api.tables.dml.?
import net.nmoncho.helenus.api.tables.dml.Select
import org.scalatest.DoNotDiscover

/** The `?` bind marker: statements with holes become FunctionN via
  * `toFunction`, taking one argument per hole (typed as the bound column) and
  * returning the rendered CQL.
  */
@DoNotDiscover
class BindMarkerSpec extends CassandraIntegrationSpec {
  import net.nmoncho.helenus._

  // ---- SELECT ---------------------------------------------------------------

  "Select.toFunction" should "produce a Function1 for a single bound key" in {
    val fn = Select.render(
      UsersTable
        .select()
        .where(UsersTable.id === ?),
      allowFiltering = false,
      prepared       = true
    )

    fn shouldBe "SELECT * FROM my_keyspace.users WHERE id = ?"
  }

  it should "render prepared markers in writing order so ? positions match arguments" in {
    val fn = Select.render(
      UsersTable
        .select(UsersTable.age)
        .where(UsersTable.username === ? and UsersTable.id === ?),
      allowFiltering = false,
      prepared       = true,
      keyOrdered     = false
    )

    // Written username-first: the prepared CQL keeps that order, so the first
    // `?` is username and the second is id, matching the produced function's
    // argument order. (Fully-bound `toCQL` still renders in CQL key order.)
    fn shouldBe "SELECT age FROM my_keyspace.users WHERE username = ? AND id = ?"
  }

  it should "mix literal and bound predicates" in {
    val fn = Select.render(
      UsersTable
        .select(UsersTable.age)
        .where(UsersTable.id === fixedId and UsersTable.username === ?),
      allowFiltering = false,
      prepared       = true
    )

    fn should include("username = ?")
  }

  it should "bind a whole Seq through in(?)" in {
    val fn = Select.render(
      UsersTable
        .select(UsersTable.age)
        .where(UsersTable.id === fixedId and UsersTable.username.in(?)),
      allowFiltering = false,
      prepared       = true
    )

    fn should include("username IN ?")
  }

  it should "bind ranges through allowFiltering for non-key columns" in {
    val fn = Select.render(
      UsersTable
        .select(UsersTable.id, UsersTable.username)
        .where(UsersTable.age >= ? and UsersTable.age <= ?)
        .allowFiltering
        .select,
      allowFiltering = true,
      prepared       = true
    )

    fn shouldBe
    "SELECT id, username FROM my_keyspace.users WHERE age >= ? AND age <= ? ALLOW FILTERING"
  }

  it should "bind key ranges without filtering" in {
    val fn = Select.render(
      SensorsTable
        .select()
        .where(
          SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts > ? and SensorsTable.ts <= ?
        ),
      allowFiltering = false,
      prepared       = true
    )

    fn should include("ts > ? AND ts <= ?")
  }

  it should "render unfilled holes as native CQL bind markers in toCQL" in {
    Select.render(
      UsersTable.select().where(UsersTable.id === ?),
      allowFiltering = false,
      prepared       = true
    ) shouldBe
    "SELECT * FROM my_keyspace.users WHERE id = ?"
  }

  it should "NOT compile execute with unbound parameters" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.id === ?).execute()"""
    )
  }

  it should "NOT compile allowFiltering.execute with unbound parameters" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.age >= ?).allowFiltering.execute()"""
    )
  }

  it should "NOT compile toFunction when the gate rejects the shape" in {
    assertTypeError(
      """UsersTable.select().where(UsersTable.age >= ? and UsersTable.age <= ?).toFunction"""
    )
  }

  // ---- DELETE ---------------------------------------------------------------

  "Delete.toFunction" should "produce a function over bound key columns" in {
    val fn = UsersTable.delete
      .where(UsersTable.id === ? and UsersTable.username === ?)
      .render(prepared = true)

    fn shouldBe "DELETE FROM my_keyspace.users WHERE id = ? AND username = ?"
  }

  it should "support column-level deletes with bound keys" in {
    val fn = UsersTable.delete
      .column(UsersTable.email)
      .where(UsersTable.id === ? and UsersTable.username === "alice")
      .render(prepared = true)

    fn shouldBe "DELETE email FROM my_keyspace.users WHERE id = ? AND username = ?"
  }

  "Delete.toFunction" should "still reject IN in DELETE, bound or not" in {
    assertTypeError(
      """UsersTable.delete
           .where(UsersTable.id === fixedId and UsersTable.username.in(?))
           .prepare"""
    )
  }

  it should "NOT compile execute with unbound parameters" in {
    assertTypeError(
      """UsersTable.delete.where(UsersTable.id === ?).execute()"""
    )
  }

}
