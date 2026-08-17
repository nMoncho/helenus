/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

// Only the parity namespace is imported: no `net.nmoncho.helenus.api.tables._` and no `shapeless._`.
import net.nmoncho.helenus.tables._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ParityNamespaceSpec extends AnyWordSpec with Matchers {

  "The tables parity namespace" should {
    "let a Table be defined and used without importing api.tables or shapeless" in {
      ParityNamespaceSpec.AccountsTable.tableName shouldBe "accounts"
      ParityNamespaceSpec.AccountsTable.keyspace shouldBe "bank"
    }
  }
}

object ParityNamespaceSpec {

  final case class Account(id: java.util.UUID, owner: String, balance: Long)

  object AccountsTable extends Table[Account]("bank", "accounts") {
    val id      = column[java.util.UUID]("id")
    val owner   = column[String]("owner")
    val balance = column[Long]("balance")

    type PK = id.Tag :: HNil
    type CK = HNil

    protected val columns = registerAllColumns(id :: owner :: balance :: HNil)
  }
}
