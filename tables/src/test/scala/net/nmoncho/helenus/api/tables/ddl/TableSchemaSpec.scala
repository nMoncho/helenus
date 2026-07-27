/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TableSchemaSpec extends AnyFlatSpec with Matchers {

  "A table" should "derive its schema from the case class" in {
    AccountsTable.create.toCQL shouldBe
    "CREATE TABLE bank.accounts (id uuid, owner text, balance bigint, PRIMARY KEY (id))"
  }

  it should "select every case-class column by default" in {
    AccountsTable.select().where(AccountsTable.id === TestValues.fixedId).toCQL shouldBe
    s"SELECT * FROM bank.accounts WHERE id = ${TestValues.fixedId}"
  }

  it should "map field names to CQL names through the naming scheme" in {
    // Events uses SnakeCase: tenantId -> tenant_id, etc.
    EventsTable.create.toCQL should include(
      "(tenant_id text, event_type text, event_id uuid, payload text,"
    )
  }

  it should "keep the precise column value type" in {
    // `:=` is column-only (unlike `===`, which ScalaTest's Matchers also
    // defines), so it truly probes the column's value type.
    assertCompiles("""UsersTable.age := 31""")
    assertTypeError("""UsersTable.age := "not an int"""")
    assertTypeError("""UsersTable.id := 42""")
  }

  it should "still infer the column type when the type argument is omitted" in {
    // scalac can infer V through FieldOfType; only IDEs need the explicit arg.
    // FIXME this should work with out typing the column
    assertCompiles(
      """import net.nmoncho.helenus._
         object Inferred extends Table[Account]("bank", "accounts") {
           val id      = column[java.util.UUID]("id")
           val owner   = column[String]("owner")
           val balance = column[Long]("balance")
           protected val columns = registerAllColumns(id :: owner :: balance :: shapeless.HNil)
           type PK = id.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }
         Inferred.owner := "someone""""
    )
  }

  // ---- the bijection: case class fields <-> registered columns ------------

  it should "NOT compile when a field has no registered column (field added to the case class)" in {
    // `balance` has a val but is missing from registerAllColumns: exactly
    // what happens when a new field is added without registering it.
    assertTypeError(
      """object Missing extends Table[Account]("bank", "accounts") {
           val id      = column[UUID]("id")
           val owner   = column[String]("owner")
           val balance = column[Long]("balance")
           protected val columns = registerAllColumns(id :: owner :: shapeless.HNil)
           type PK = id.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  // NOTE (not testable with assertTypeError): forgetting the registration
  // entirely also fails real compilation, with "object creation impossible.
  // Missing implementation for member ... columns" -- but that diagnostic
  // comes from the refchecks phase, which scalatest's typecheck-based
  // assertTypeError macro does not run. Verified against scalac directly.

  it should "NOT compile when a computed column is included in the registration" in {
    assertTypeError(
      """object WithComputed extends Table[Account]("bank", "accounts") {
           val id      = column[UUID]("id")
           val owner   = column[String]("owner")
           val balance = column[Long]("balance")
           val extra   = computedColumn("extra")(_.owner.length)
           protected val columns = registerAllColumns(id :: owner :: balance :: extra :: shapeless.HNil)
           type PK = id.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  it should "NOT compile a column reference to a field absent from the case class" in {
    assertTypeError(
      """object Broken extends Table[Account]("bank", "accounts") {
           val nope = column[UUID]("nope")
           type PK = nope.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  it should "NOT compile a column whose type argument does not match the field's type" in {
    assertTypeError(
      """object Broken2 extends Table[Account]("bank", "accounts") {
           val id = column[String]("id") // Account.id is a UUID
           type PK = id.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  // ---- frozenColumn: like column, but stating only the wrapped type -------

  it should "declare a Frozen field with just the wrapped type via frozenColumn" in {
    // SnapshotsTable.labels/tags are declared `frozenColumn[Set[String]](...)`
    // for a `Frozen[Set[String]]` field; behaves exactly like
    // `column[Frozen[Set[String]]](...)` (see DdlSpec / IndexSpec).
    assertCompiles("""SnapshotsTable.tags := Frozen(Set("x"))""")
    assertTypeError("""SnapshotsTable.tags := Set("x")""")
  }

  it should "NOT compile frozenColumn with a wrapped type that doesn't match the field's" in {
    assertTypeError(
      """object Broken3 extends Table[Snapshot]("blog", "snapshots") {
           val labels = frozenColumn[List[String]]("labels") // field is Frozen[Set[String]], not Frozen[List[String]]
           type PK = labels.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  it should "NOT compile frozenColumn on a field that isn't Frozen at all" in {
    assertTypeError(
      """object Broken4 extends Table[Account]("bank", "accounts") {
           val owner = frozenColumn[String]("owner") // Account.owner is a plain String, not Frozen[String]
           type PK = owner.Tag :: shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }

  it should "NOT compile a table definition for a case class with a field lacking a CQLType" in {
    // The ColumnsFor / InsertValues derivations are constructor implicits, so
    // the completeness check fails at the object definition itself.
    assertTypeError(
      """case class HasWeird(id: UUID, weird: Thread)
         object WeirdTable extends Table[HasWeird]("k", "t") {
           val id = column[UUID]("id")
           val id = column[Thread]("weird")
           type PK = shapeless.HNil
           type CK = shapeless.HNil
         }"""
    )
  }
}
