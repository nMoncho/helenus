/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package integration

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

import net.nmoncho.helenus.api.tables.dml.Batch
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

/** Exercises the `executeAsync` surface of the DSL against a real (embedded)
  * Cassandra. Every builder exposes an async execute (Insert, Select, Update,
  * Delete, Batch, and the CREATE / TRUNCATE / DROP DDL), but the rest of the
  * integration suite only drives the synchronous `execute()`; these cases cover
  * the `Future`-returning paths end to end.
  */
@DoNotDiscover
class AsyncExecuteIntegrationSpec
    extends CassandraIntegrationSpec
    with BeforeAndAfterEach
    with ScalaFutures
    with OptionValues {

  // Lifts the synchronous `CqlSession` from the harness into the `Future[CqlSession]`
  // that the DML `executeAsync` methods expect (via `cqlSessionAdapter`).
  import net.nmoncho.helenus._

  private implicit val ec: ExecutionContext = ExecutionContext.global

  // The embedded single node can be slow to answer under load; give futures headroom.
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

  private def userByName(username: String): Option[User] =
    rows(
      UsersTable.select().where(UsersTable.id === id and UsersTable.username === username).execute()
    ).headOption

  private def seedAlice(age: Int = 30): Unit =
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := age)
        .toCQL
    )

  "Insert.executeAsync" should "write a row that can be read back" in {
    whenReady(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
        .executeAsync()
    ) { rs =>
      rs.wasApplied() shouldBe true
    }

    val row = userByName("alice").value
    row.username shouldBe "alice"
    row.age shouldBe 30
  }

  "Select.executeAsync" should "read rows asynchronously through a MappedAsyncPagingIterable" in {
    seedAlice()

    whenReady(
      UsersTable
        .select()
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .executeAsync()
    ) { page =>
      page.currentPage().asScala.map(_.username).toList should contain("alice")
    }
  }

  "Update.executeAsync" should "modify a row asynchronously" in {
    seedAlice(age = 30)

    whenReady(
      UsersTable.update
        .set(UsersTable.age := 31)
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .executeAsync()
    ) { rs =>
      rs.wasApplied() shouldBe true
    }

    userByName("alice").value.age shouldBe 31
  }

  "Delete.executeAsync" should "remove a row asynchronously" in {
    seedAlice()

    whenReady(
      UsersTable.delete
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .executeAsync()
    ) { rs =>
      rs.wasApplied() shouldBe true
    }

    userByName("alice") shouldBe None
  }

  "Batch.executeAsync" should "apply every statement of an all-literal batch" in {
    whenReady(
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
      ).executeAsync()
    ) { rs =>
      rs.wasApplied() shouldBe true
    }

    userByName("alice").value.age shouldBe 30
    userByName("bob").value.age shouldBe 25
  }

  "DDL executeAsync" should "run TRUNCATE, DROP, and CREATE asynchronously" in {
    seedAlice()

    // TRUNCATE asynchronously, then confirm the table is empty.
    whenReady(UsersTable.truncate.executeAsync())(_ => ())
    userByName("alice") shouldBe None

    // DROP then re-CREATE asynchronously, leaving the table present for later specs.
    whenReady(UsersTable.drop.ifExists.executeAsync())(_ => ())
    whenReady(UsersTable.create.ifNotExists.executeAsync())(_ => ())

    // The re-created table is usable again.
    seedAlice(age = 7)
    userByName("alice").value.age shouldBe 7
  }
}
