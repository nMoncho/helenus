/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import java.util.UUID

import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.SnakeCase
import shapeless._

case class User(
    id: UUID,
    username: String,
    age: Int,
    email: String,
    tags: Set[String],
    metadata: Map[String, String]
)

object UsersTable extends Table[User]("my_keyspace", "users") {

  val id       = column[UUID]("id")
  val username = column[String]("username")
  val age      = column[Int]("age")
  val email    = column[String]("email")
  val tags     = column[Set[String]]("tags")
  val metadata = column[Map[String, String]]("metadata")

  type PK = id.Tag :: HNil
  type CK = username.Tag :: HNil

  protected val columns: Table.AllColumns = registerAllColumns(
    id :: username :: age :: email :: tags :: metadata :: HNil
  )
}

case class Event(tenantId: String, eventType: String, eventId: UUID, payload: String)

object EventsTable extends Table[Event]("analytics", "events") {
  override protected def naming: ColumnNamingScheme = SnakeCase

  val tenantId  = column[String]("tenantId")
  val eventType = column[String]("eventType")
  val eventId   = column[UUID]("eventId")
  val payload   = column[String]("payload")

  protected val columns = registerAllColumns(tenantId :: eventType :: eventId :: payload :: HNil)

  // Composite partition key + clustering column
  type PK = tenantId.Tag :: eventType.Tag :: HNil
  type CK = eventId.Tag :: HNil
}

case class Sensors(deviceId: UUID, year: Int, ts: Long, reading: Double)

object SensorsTable extends Table[Sensors]("iot", "sensor_readings") {
  override protected def naming: ColumnNamingScheme = SnakeCase

  val deviceId = column[UUID]("deviceId")
  val year     = column[Int]("year")
  val ts       = column[Long]("ts")
  val reading  = column[Double]("reading")

  protected val columns = registerAllColumns(deviceId :: year :: ts :: reading :: HNil)

  // Single partition key + two clustering columns (prefix rules apply).
  // `ts` is declared descending; a bare column tag means ascending.
  type PK = deviceId.Tag :: HNil
  type CK = year.Tag :: ts.Desc :: HNil
}

// A table with a computed column (`shard`, derived from `name`) that is part
// of the partition key. `shard` is not a field of Metric, so the RowMapper
// ignores it, but it is written by insertFrom and required in queries.
case class Metric(id: UUID, name: String, value: Double)

object MetricsTable extends Table[Metric]("monitoring", "metrics") {
  val id    = column[UUID]("id")
  val name  = column[String]("name")
  val value = column[Double]("value")
  // NOTE: no val ascription here (`: Column[Int]` would widen away the Tag
  // refinement and break the PK declaration below); Col is inferred from the
  // compute lambda by plain inference, which IDEs handle fine.
  val shard = computedColumn("shard")(_.name.length)

  // Computed columns are not fields of Metric and are not registered.
  protected val columns = registerAllColumns(id :: name :: value :: HNil)

  type PK = shard.Tag :: id.Tag :: HNil
  type CK = name.Tag :: HNil
}

object TestValues {
  val fixedId: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
}
