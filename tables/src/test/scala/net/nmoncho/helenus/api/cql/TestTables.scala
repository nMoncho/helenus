/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import java.util.UUID

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
