/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package integration

import java.util.UUID

import scala.concurrent.ExecutionContext

import net.nmoncho.helenus._
import net.nmoncho.helenus.api.tables.dml.?
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

/** Exercises the `ToPrepared` arity machinery beyond the 1-to-3 markers the rest of
  * the suite uses. `UsersTable` has six columns, so an all-`?` INSERT drives arities
  * 4, 5, and 6 (each a distinct `ToPrepared` instance) end to end, both synchronously
  * (`prepare`) and asynchronously (`prepareAsync`). Arities 7 to 22 mirror these exactly
  * and are marked `$COVERAGE-OFF$` in `ToPrepared`, so a wider table is not needed here.
  */
@DoNotDiscover
class PreparedArityIntegrationSpec
    extends CassandraIntegrationSpec
    with BeforeAndAfterEach
    with ScalaFutures {

  // Lifts the harness `CqlSession` into the `Future[CqlSession]` that `prepareAsync` expects.
  private implicit val ec: ExecutionContext = ExecutionContext.global

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds))

  private val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()
    execute(UsersTable.drop.ifExists.toCQL)
    execute(UsersTable.create.toCQL)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    execute("TRUNCATE my_keyspace.users")
  }

  private def ageOf(username: String): Option[Int] =
    rows(
      UsersTable.select().where(UsersTable.id === id and UsersTable.username === username).execute()
    ).headOption.map(_.age)

  "Insert.prepare" should "prepare and bind arities 4, 5, and 6 synchronously" in {
    val p4 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .prepare
    p4.execute(id, "u4", 4, "u4@example.com")

    val p5 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .value(UsersTable.tags := ?)
      .prepare
    p5.execute(id, "u5", 5, "u5@example.com", Set("beta"))

    val p6 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .value(UsersTable.tags := ?)
      .value(UsersTable.metadata := ?)
      .prepare
    p6.execute(id, "u6", 6, "u6@example.com", Set("beta"), Map("k" -> "v"))

    ageOf("u4") shouldBe Some(4)
    ageOf("u5") shouldBe Some(5)
    ageOf("u6") shouldBe Some(6)
  }

  "Insert.prepareAsync" should "prepare and bind arities 4, 5, and 6 asynchronously" in {
    // Bind each `prepareAsync` to a val so its polymorphic result type is inferred before
    // it is handed to `whenReady` (the same reason the sync cases above use vals).
    val f4 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .prepareAsync
    whenReady(f4)(_.execute(id, "a4", 40, "a4@example.com"))

    val f5 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .value(UsersTable.tags := ?)
      .prepareAsync
    whenReady(f5)(_.execute(id, "a5", 50, "a5@example.com", Set("beta")))

    val f6 = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .value(UsersTable.email := ?)
      .value(UsersTable.tags := ?)
      .value(UsersTable.metadata := ?)
      .prepareAsync
    whenReady(f6)(_.execute(id, "a6", 60, "a6@example.com", Set("beta"), Map("k" -> "v")))

    ageOf("a4") shouldBe Some(40)
    ageOf("a5") shouldBe Some(50)
    ageOf("a6") shouldBe Some(60)
  }
}
