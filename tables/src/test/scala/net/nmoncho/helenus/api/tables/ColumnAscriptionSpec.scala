/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import shapeless.test.illTyped

/** F8: ascribing a column val with an explicit type (`val id: Column[X] = column("id")`) silently
  * widens away the `Tag` singleton, which used to break `PK` / `CK` derivation with a cryptic error.
  * It must now fail to compile with an actionable message. These checks run at the test's own
  * compile time; no live Cassandra is involved.
  */
class ColumnAscriptionSpec extends AnyWordSpec with Matchers {

  "A table whose column vals are not ascribed" should {
    "compile and expose its keys" in {
      assertCompiles("""
        import java.util.UUID
        import net.nmoncho.helenus._
        import net.nmoncho.helenus.tables._

        final case class User(id: UUID, name: String)

        object GoodTable extends Table[User]("ks", "users") {
          val id   = column[UUID]("id")
          val name = column[String]("name")

          type PK = id.Tag :: HNil
          type CK = HNil

          protected val columns = registerAllColumns(id :: name :: HNil)
        }

        GoodTable.create
      """)
    }
  }

  "A table with an ascribed column val" should {
    "fail to compile with an actionable message" in {
      illTyped(
        """
        import java.util.UUID
        import net.nmoncho.helenus._
        import net.nmoncho.helenus.tables._

        final case class User(id: UUID, name: String)

        object BadTable extends Table[User]("ks", "users") {
          val id: Column[UUID] = column[UUID]("id") // ascribed: drops the Tag singleton
          val name             = column[String]("name")

          type PK = id.Tag :: HNil
          type CK = HNil

          protected val columns = registerAllColumns(id :: name :: HNil)
        }

        BadTable.create
        """,
        "(?s).*widens away the.*"
      )
    }
  }
}
