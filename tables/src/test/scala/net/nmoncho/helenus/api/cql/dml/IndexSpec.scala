/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import net.nmoncho.helenus.api.cql.TestValues.fixedId
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Secondary indexes: `Table.index`, `createIndexes` DDL, and the
  * index-aware `contains` execute gate.
  */
class IndexSpec extends AnyFlatSpec with Matchers {
  import net.nmoncho.helenus._

  "Table.index" should "register a CREATE INDEX statement per declared index" in {
    ArticlesTable.createIndexes.map(_.toCQL) shouldBe
    Seq("CREATE INDEX articles_tags_idx ON blog.articles (tags)")
  }

  it should "not register an index for a column that was not wrapped in index(...)" in {
    ArticlesTable.createIndexes.map(_.target) should not contain "categories"
  }

  it should "support IF NOT EXISTS" in {
    ArticlesTable.createIndexes.head.ifNotExists.toCQL shouldBe
    "CREATE INDEX IF NOT EXISTS articles_tags_idx ON blog.articles (tags)"
  }

  "contains on an indexed column" should "execute without allowFiltering" in {
    val cql = ArticlesTable
      .select()
      .where(ArticlesTable.tags.contains("scala"))
      .toCQL

    cql shouldBe "SELECT * FROM blog.articles WHERE tags CONTAINS 'scala'"
  }

  it should "work on quering only with the partitioning key" in {
    val cql = ArticlesTable
      .select()
      .where(ArticlesTable.id === fixedId)
      .toCQL

    cql should include(s"WHERE id = $fixedId")
  }

  it should "combine with a full primary-key restriction and still execute" in {
    val cql = ArticlesTable
      .select()
      .where(ArticlesTable.id === fixedId and ArticlesTable.tags.contains("scala"))
      .toCQL

    cql should include(s"WHERE id = $fixedId AND tags CONTAINS 'scala'")
  }

  "contains on a non-indexed column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """ArticlesTable.select().where(ArticlesTable.categories.contains("scala")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = ArticlesTable
      .select()
      .where(ArticlesTable.categories.contains("scala"))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.articles WHERE categories CONTAINS 'scala' ALLOW FILTERING"
  }

  "An indexed column" should "still work as an ordinary column (assignment, equality, key)" in {
    val cql = ArticlesTable.insert
      .value(ArticlesTable.id := fixedId)
      .value(ArticlesTable.tags := Set("scala", "cql"))
      .toCQL

    cql should include("(id, tags) VALUES")
  }

  // ---- containsKey (maps) --------------------------------------------------

  "Table.index" should "register both a values and a KEYS index for an indexed map column" in {
    ProfilesTable.createIndexes.map(_.toCQL) shouldBe
    Seq(
      "CREATE INDEX profiles_attributes_idx ON blog.profiles (attributes)",
      "CREATE INDEX profiles_attributes_keys_idx ON blog.profiles (KEYS(attributes))",
      "CREATE INDEX profiles_attributes_entries_idx ON blog.profiles (ENTRIES(attributes))"
    )
  }

  "containsKey on an indexed column" should "execute without allowFiltering" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.attributes.containsKey("color"))
      .toCQL

    cql shouldBe "SELECT * FROM blog.profiles WHERE attributes CONTAINS KEY 'color'"
  }

  it should "combine with a full primary-key restriction and still execute" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.id === fixedId and ProfilesTable.attributes.containsKey("color"))
      .toCQL

    cql should include(s"WHERE id = $fixedId AND attributes CONTAINS KEY 'color'")
  }

  "containsKey on a non-indexed column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """ProfilesTable.select().where(ProfilesTable.settings.containsKey("color")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.settings.containsKey("color"))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.profiles WHERE settings CONTAINS KEY 'color' ALLOW FILTERING"
  }

  "An indexed map column" should "still work as an ordinary column (assignment, equality, key)" in {
    val cql = ProfilesTable.insert
      .value(ProfilesTable.id := fixedId)
      .value(ProfilesTable.attributes := Map("color" -> "red"))
      .toCQL

    cql should include("(id, attributes) VALUES")
  }

  // ---- frozen collections: FULL index instead of KEYS / VALUES -------------

  "Table.index" should "register a single FULL index for an indexed frozen column" in {
    SnapshotsTable.createIndexes.map(_.toCQL) shouldBe
    Seq("CREATE INDEX snapshots_labels_full_idx ON blog.snapshots (FULL(labels))")
  }

  it should "not register an index for a frozen column that was not wrapped in index(...)" in {
    SnapshotsTable.createIndexes.map(_.target) should not contain "tags"
  }

  "contains" should "NOT compile on a Frozen column, indexed or not" in {
    assertTypeError(
      """SnapshotsTable.select().where(SnapshotsTable.labels.contains("scala")).allowFiltering.toCQL"""
    )
    assertTypeError(
      """SnapshotsTable.select().where(SnapshotsTable.tags.contains("scala")).allowFiltering.toCQL"""
    )
  }

  "containsKey" should "NOT compile on a Frozen column either (it isn't a Map)" in {
    assertTypeError(
      """SnapshotsTable.select().where(SnapshotsTable.labels.containsKey("scala")).allowFiltering.toCQL"""
    )
  }

  "=== on an indexed Frozen column" should "execute without allowFiltering" in {
    val cql = SnapshotsTable
      .select()
      .where(SnapshotsTable.labels === Frozen(Set("scala")))
      .toCQL

    cql shouldBe "SELECT * FROM blog.snapshots WHERE labels = {'scala'}"
  }

  "=== on a non-indexed Frozen column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """SnapshotsTable.select().where(SnapshotsTable.tags === Frozen(Set("backend"))).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = SnapshotsTable
      .select()
      .where(SnapshotsTable.tags === Frozen(Set("backend")))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.snapshots WHERE tags = {'backend'} ALLOW FILTERING"
  }

  "A Frozen column" should "still work as an ordinary column (assignment, key)" in {
    val cql = SnapshotsTable.insert
      .value(SnapshotsTable.id := fixedId)
      .value(SnapshotsTable.labels := Frozen(Set("scala", "cql")))
      .toCQL

    cql should include("(id, labels) VALUES")
  }

  // ---- === on any indexed column, not just collections ----------------------

  "Table.index" should "work on a plain scalar column, not just collections" in {
    CustomersTable.createIndexes.map(_.toCQL) shouldBe
    Seq("CREATE INDEX customers_email_idx ON blog.customers (email)")
  }

  "=== on an indexed scalar column" should "execute without allowFiltering" in {
    val cql = CustomersTable.select().where(CustomersTable.email === "alice@example.com").toCQL
    cql shouldBe "SELECT * FROM blog.customers WHERE email = 'alice@example.com'"
  }

  it should "combine with a full primary-key restriction and still execute" in {
    val cql = CustomersTable
      .select()
      .where(CustomersTable.id === fixedId and CustomersTable.email === "alice@example.com")
      .toCQL

    cql should include(s"WHERE id = $fixedId AND email = 'alice@example.com'")
  }

  "=== on a non-indexed scalar column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """CustomersTable.select().where(CustomersTable.age === 30).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = CustomersTable.select().where(CustomersTable.age === 30).allowFiltering.toCQL
    cql shouldBe "SELECT * FROM blog.customers WHERE age = 30 ALLOW FILTERING"
  }

  "An indexed scalar column" should "still work as an ordinary column (assignment, key)" in {
    val cql = CustomersTable.insert
      .value(CustomersTable.id := fixedId)
      .value(CustomersTable.email := "alice@example.com")
      .toCQL

    cql should include("(id, email) VALUES")
  }

  // ---- Table.index's optional custom name ------------------------------

  "Table.index" should "use a custom name verbatim for a single-target column" in {
    OrdersTable.createIndexes.map(_.toCQL) should contain(
      "CREATE INDEX orders_status_lookup ON blog.orders (status)"
    )
  }

  it should "still suffix a custom name for a multi-target column, to keep both indexes distinct" in {
    OrdersTable.createIndexes.map(_.toCQL) should contain allOf (
      "CREATE INDEX orders_labels_lookup_idx ON blog.orders (labels)",
      "CREATE INDEX orders_labels_lookup_keys_idx ON blog.orders (KEYS(labels))"
    )
  }

  it should "default to tableName_columnName when no name is given" in {
    ArticlesTable.createIndexes.map(_.indexName) shouldBe Seq("articles_tags_idx")
  }

  "=== on a custom-named indexed column" should "execute without allowFiltering, same as an auto-named one" in {
    val cql = OrdersTable.select().where(OrdersTable.status === "shipped").toCQL
    cql shouldBe "SELECT * FROM blog.orders WHERE status = 'shipped'"
  }
}
