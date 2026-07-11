/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import java.util.UUID

import shapeless.::
import shapeless.HNil

case class User(
    id: UUID,
    username: String,
    age: Int,
    email: String,
    tags: Set[String],
    metadata: Map[String, String]
)

object UsersTable extends Table[User]("my_keyspace", "users") {
  import net.nmoncho.helenus._

  val id: Column[UUID]       = column[UUID]("id")
  val username: Column[String] = column[String]("username")
  val age: Column[Int]      = column[Int]("age")
  val email: Column[String]    = column[String]("email")
  val tags: Column[Set[String]]     = column[Set[String]]("tags")
  val metadata: Column[Map[String,String]] = column[Map[String, String]]("metadata")

  type PK = id.Tag :: HNil
  type CK = username.Tag :: HNil

  override protected val columns: Table.AllColumns = registerAllColumns(
    id :: username :: age :: email :: tags :: metadata :: HNil
  )
}
