/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.integration

import java.util.UUID

import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.SensorsTable
import net.nmoncho.helenus.api.cql.UsersTable
import net.nmoncho.helenus.api.cql.dml.?
import org.scalatest.BeforeAndAfterEach
import org.scalatest.DoNotDiscover

@DoNotDiscover
class UpdateIntegrationSpec extends CassandraIntegrationSpec with BeforeAndAfterEach {

  private val id       = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val deviceId = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()
    Seq(
      UsersTable.drop.ifExists.toCQL,
      UsersTable.create.toCQL,
      SensorsTable.drop.ifExists.toCQL,
      SensorsTable.create.toCQL
    ).foreach(execute)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    execute("TRUNCATE my_keyspace.users")
    execute(s"INSERT INTO my_keyspace.users (id, username, age) VALUES ($id, 'alice', 30)")
    execute(s"INSERT INTO my_keyspace.users (id, username, age) VALUES ($id, 'bob', 25)")
  }

  private def ageOf(username: String): Int =
    rows(
      UsersTable
        .select(UsersTable.age)
        .where(UsersTable.id === id and UsersTable.username === username)
        .execute
    ).head.getInt("age")

  "Update.execute" should "update exactly the row identified by the primary key" in {
    execute(
      UsersTable.update
        .set(UsersTable.age := 31)
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .execute
    )

    ageOf("alice") shouldBe 31
    ageOf("bob") shouldBe 25
  }

  it should "update several rows with IN on the last primary-key component" in {
    execute(
      UsersTable.update
        .set(UsersTable.age := 50)
        .where(UsersTable.id === id and UsersTable.username.in(Seq("alice", "bob")))
        .execute
    )

    ageOf("alice") shouldBe 50
    ageOf("bob") shouldBe 50
  }

  it should "apply USING TTL" in {
    execute(
      UsersTable.update
        .usingTTL(7200)
        .set(UsersTable.age := 31)
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .execute
    )

    val ttl = rows(
      s"SELECT TTL(age) FROM my_keyspace.users WHERE id = $id AND username = 'alice'"
    ).head.getInt(0)
    ttl should be > 0
    ttl should be <= 7200
  }

  it should "not apply a write with an older USING TIMESTAMP" in {
    // The seed row was written at server-now microseconds; timestamp 1000 is older.
    execute(
      UsersTable.update
        .usingTimestamp(1000L)
        .set(UsersTable.age := 99)
        .where(UsersTable.id === id and UsersTable.username === "alice")
        .execute
    )

    ageOf("alice") shouldBe 30
  }

  it should "report unapplied IF EXISTS on a missing row" in {
    val result = execute(
      UsersTable.update
        .set(UsersTable.age := 31)
        .where(UsersTable.id === id and UsersTable.username === "nobody")
        .ifExists
        .execute
    )

    result.wasApplied() shouldBe false
  }

  it should "update through a function, SET parameters first then WHERE parameters" in {
    val setAge = UsersTable.update
      .set(UsersTable.age := ?)
      .where(UsersTable.id === id and UsersTable.username === ?)
      .toFunction

    execute(setAge(60, "alice"))
    execute(setAge(61, "bob"))

    ageOf("alice") shouldBe 60
    ageOf("bob") shouldBe 61
  }

  // ---- shapes the gate rejects, confirmed rejected by the server ----------

  it should "be rejected by Cassandra when clustering columns are missing" in {
    an[InvalidQueryException] should be thrownBy
    execute(UsersTable.update.set(UsersTable.age := 31).where(UsersTable.id === id).toCQL)
  }

  it should "be rejected by Cassandra with a range predicate" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      SensorsTable.update
        .set(SensorsTable.reading := 1.0)
        .where(
          SensorsTable.deviceId === deviceId and SensorsTable.year === 2026 and SensorsTable.ts > 100L
        )
        .toCQL
    )
  }

  it should "be rejected by Cassandra when a non-key column is constrained" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      UsersTable.update
        .set(UsersTable.age := 31)
        .where(
          UsersTable.id === id and UsersTable.username === "alice" and UsersTable.email === "a@b.c"
        )
        .toCQL
    )
  }
}
