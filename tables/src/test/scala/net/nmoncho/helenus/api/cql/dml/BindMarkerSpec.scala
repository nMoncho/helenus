/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

///*
// * Copyright 2021 the original author or authors
// *
// * SPDX-License-Identifier: MIT
// */
//
//package net.nmoncho.helenus.api.cql
//package dml
//
//import java.util.UUID
//
//import net.nmoncho.helenus.api.cql.TestValues.fixedId
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers
//
///** The `?` bind marker: statements with holes become FunctionN via
//  * `toFunction`, taking one argument per hole (typed as the bound column) and
//  * returning the rendered CQL.
//  */
//class BindMarkerSpec extends AnyFlatSpec with Matchers {
//  import net.nmoncho.helenus._
//
//  // ---- SELECT ---------------------------------------------------------------
//
//  "Select.toFunction" should "produce a Function1 for a single bound key" in {
//    val fn: UUID => String = UsersTable
//      .select()
//      .where(UsersTable.id === ?)
//      .toFunction
//
//    fn(fixedId) shouldBe
//    s"SELECT * FROM my_keyspace.users WHERE id = $fixedId"
//  }
//
//  it should "produce a Function2 with parameters in writing order despite CQL reordering" in {
//    val fn: (String, UUID) => String = UsersTable
//      .select(UsersTable.age)
//      .where(UsersTable.username === ? and UsersTable.id === ?)
//      .toFunction
//
//    // written username-first, rendered id-first: arguments still bind by writing order
//    fn("alice", fixedId) shouldBe
//    s"SELECT age FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
//  }
//
//  it should "mix literal and bound predicates" in {
//    val fn: String => String = UsersTable
//      .select(UsersTable.age)
//      .where(UsersTable.id === fixedId and UsersTable.username === ?)
//      .toFunction
//
//    fn("o'reilly") should include("username = 'o''reilly'")
//  }
//
//  it should "bind a whole Seq through in(?)" in {
//    val fn: Seq[String] => String = UsersTable
//      .select(UsersTable.age)
//      .where(UsersTable.id === fixedId and UsersTable.username.in(?))
//      .toFunction
//
//    fn(Seq("alice", "bob")) should include("username IN ('alice', 'bob')")
//  }
//
//  it should "bind ranges through allowFiltering for non-key columns" in {
//    val fn: (Int, Int) => String = UsersTable
//      .select(UsersTable.id, UsersTable.username)
//      .where(UsersTable.age >= ? and UsersTable.age <= ?)
//      .allowFiltering
//      .toFunction
//
//    fn(18, 65) shouldBe
//    "SELECT id, username FROM my_keyspace.users " +
//    "WHERE age >= 18 AND age <= 65 ALLOW FILTERING"
//  }
//
//  it should "bind key ranges without filtering" in {
//    val fn: (Long, Long) => String = SensorsTable
//      .select()
//      .where(
//        SensorsTable.deviceId === fixedId and SensorsTable.year === 2026 and SensorsTable.ts > ? and SensorsTable.ts <= ?
//      )
//      .toFunction
//
//    fn(100L, 200L) should include("ts > 100 AND ts <= 200")
//  }
//
//  it should "render unfilled holes as native CQL bind markers in toCQL" in {
//    UsersTable.select().where(UsersTable.id === ?).toCQL shouldBe
//    "SELECT * FROM my_keyspace.users WHERE id = ?"
//  }
//
//  it should "NOT compile execute with unbound parameters" in {
//    assertTypeError(
//      """UsersTable.select().where(UsersTable.id === ?).execute()"""
//    )
//  }
//
//  it should "NOT compile toFunction when the gate rejects the shape" in {
//    assertTypeError(
//      """UsersTable.select().where(UsersTable.age >= ? and UsersTable.age <= ?).toFunction"""
//    )
//  }
//
//  // ---- DELETE ---------------------------------------------------------------
//
////  "Delete.toFunction" should "produce a function over bound key columns" in {
////    val fn: (UUID, String) => String = UsersTable.delete
////      .where(UsersTable.id === ? and UsersTable.username === ?)
////      .toFunction
////
////    fn(fixedId, "alice") shouldBe
////    s"DELETE FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
////  }
////
////  it should "support column-level deletes with bound keys" in {
////    val fn: UUID => String = UsersTable.delete
////      .column(UsersTable.email)
////      .where(UsersTable.id === ? and UsersTable.username === "alice")
////      .toFunction
////
////    fn(fixedId) shouldBe
////    s"DELETE email FROM my_keyspace.users WHERE id = $fixedId AND username = 'alice'"
////  }
//
//  "Delete.toFunction" should "still reject IN in DELETE, bound or not" in {
//    assertTypeError(
//      """UsersTable.delete
//           .where(UsersTable.id === fixedId and UsersTable.username.in(?))
//           .toFunction"""
//    )
//  }
//
//  it should "NOT compile execute with unbound parameters" in {
//    assertTypeError(
//      """UsersTable.delete.where(UsersTable.id === ?).execute()"""
//    )
//  }
//
//}
