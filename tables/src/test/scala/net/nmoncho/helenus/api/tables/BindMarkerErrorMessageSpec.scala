/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import shapeless.test.illTyped

/** G1: the SELECT and DELETE execution gates reject invalid shapes at compile
  * time with an actionable `@implicitNotFound` message. The `assertTypeError`
  * checks in [[net.nmoncho.helenus.api.tables.integration.BindMarkerSpec]]
  * prove only that such shapes fail to compile; these checks additionally pin
  * the message wording, so a future edit that guts the message (or replaces a
  * gate with a cryptic implicit-not-found error) is caught as a regression.
  *
  * All checks run at this test's own compile time via `illTyped`; no live
  * Cassandra is involved, and each negative check is paired with a positive
  * control so the gate is shown to reject only the invalid shape.
  */
class BindMarkerErrorMessageSpec extends AnyWordSpec with Matchers {

  "The SELECT gate" should {
    "reject a non-primary-key restriction with the ALLOW FILTERING guidance" in {
      // `age` is neither partition nor clustering key, so the query can only run
      // with ALLOW FILTERING. The `@implicitNotFound` on CanSelect must say so
      // and point at `.allowFiltering.execute`.
      illTyped(
        """
        import net.nmoncho.helenus._
        UsersTable.select().where(UsersTable.age > 25).toCQL
        """,
        "(?s).*cannot be executed without ALLOW FILTERING.*allowFiltering\\.execute instead\\..*"
      )
    }

    "still accept a valid primary-key restriction" in {
      assertCompiles(
        """
        import net.nmoncho.helenus._
        UsersTable
          .select()
          .where(UsersTable.id === java.util.UUID.randomUUID() and UsersTable.username === "alice")
          .toCQL
        """
      )
    }
  }

  "The DELETE gate" should {
    "reject a clustering-only restriction with the invalid-CQL guidance" in {
      // Constraining only the clustering column `username` leaves the partition
      // key `id` unbound, which is not a valid DELETE. The `@implicitNotFound`
      // on CanDelete must explain the primary-key requirement.
      illTyped(
        """
        import net.nmoncho.helenus._
        UsersTable.delete.where(UsersTable.username === "alice").toCQL
        """,
        "(?s).*This DELETE is not valid CQL and cannot be executed\\..*"
      )
    }

    "still accept a full primary-key restriction" in {
      assertCompiles(
        """
        import net.nmoncho.helenus._
        UsersTable.delete
          .where(UsersTable.id === java.util.UUID.randomUUID() and UsersTable.username === "alice")
          .toCQL
        """
      )
    }
  }
}
