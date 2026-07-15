/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.integration

import java.util.UUID

import scala.jdk.CollectionConverters._

import net.nmoncho.helenus.api.cql.UsersTable
import net.nmoncho.helenus.api.cql.dml.?
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover

@DoNotDiscover
class InsertIntegrationSpec extends CassandraIntegrationSpec with BeforeAndAfterEach {

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

  private def aliceRow() =
    rows(
      UsersTable.select().where(UsersTable.id === id and UsersTable.username === "alice").execute
    ).headOption

  "Insert" should "write a row that can be read back" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
        .toCQL
    )

    val row = aliceRow().get
    row.getUuid("id") shouldBe id
    row.getString("username") shouldBe "alice"
    row.getInt("age") shouldBe 30
  }

  it should "round-trip values containing single quotes" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "o'reilly")
        .toCQL
    )

    val row = rows(UsersTable.select().where(UsersTable.id === id).execute).head
    row.getString("username") shouldBe "o'reilly"
  }

  it should "not overwrite an existing row with IF NOT EXISTS" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
        .toCQL
    )

    val second = execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 99)
        .ifNotExists
        .toCQL
    )

    second.wasApplied() shouldBe false
    aliceRow().get.getInt("age") shouldBe 30
  }

  it should "apply USING TTL" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
        .usingTTL(3600)
        .toCQL
    )

    val ttl = rows(
      s"SELECT TTL(age) FROM my_keyspace.users WHERE id = $id AND username = 'alice'"
    ).head.getInt(0)
    ttl should be > 0
    ttl should be <= 3600
  }

  it should "apply USING TIMESTAMP" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.age := 30)
        .usingTimestamp(1_000_000L)
        .toCQL
    )

    val writetime = rows(
      s"SELECT WRITETIME(age) FROM my_keyspace.users WHERE id = $id AND username = 'alice'"
    ).head.getLong(0)
    writetime shouldBe 1_000_000L
  }

  it should "insert through a function produced from bound values" in {
    val insertUser = UsersTable.insert
      .value(UsersTable.id := ?)
      .value(UsersTable.username := ?)
      .value(UsersTable.age := ?)
      .toFunction

    execute(insertUser(id, "alice", 30))
    execute(insertUser(id, "bob", 25))

    rows(UsersTable.select().where(UsersTable.id === id).execute) should have size 2
  }

  it should "round-trip collection values" in {
    execute(
      UsersTable.insert
        .value(UsersTable.id := id)
        .value(UsersTable.username := "alice")
        .value(UsersTable.tags := Set("admin", "beta"))
        .value(UsersTable.metadata := Map("k" -> "v"))
        .toCQL
    )

    val row = aliceRow().get
    row.getSet("tags", classOf[String]).asScala shouldBe Set("admin", "beta")
    row.getMap("metadata", classOf[String], classOf[String]).asScala shouldBe Map("k" -> "v")
  }
}
