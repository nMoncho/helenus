/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import net.nmoncho.helenus.api.tables.TestValues.fixedId
import net.nmoncho.helenus.api.tables.ddl.CreateIndex
import net.nmoncho.helenus.api.tables.ddl.IndexKind
import net.nmoncho.helenus.api.tables.ddl.SAI
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

  // ---- Table.index's optional kind: Secondary (default) vs Custom (SAI) ----

  "Table.index" should "default to a plain secondary index (kind = Secondary)" in {
    AuthorsTable.createIndexes.map(_.toCQL) should contain(
      "CREATE INDEX authors_handle_idx ON blog.authors (handle)"
    )
  }

  it should "render a CUSTOM index with USING and WITH OPTIONS for kind = Custom" in {
    AuthorsTable.createIndexes.map(_.toCQL) should contain(
      "CREATE CUSTOM INDEX authors_bio_idx ON blog.authors (bio) " +
        "USING 'org.apache.cassandra.index.sai.StorageAttachedIndex' WITH OPTIONS = {'case_sensitive': 'false'}"
    )
  }

  it should "omit WITH OPTIONS when kind = Custom has none" in {
    CreateIndex(AuthorsTable, "authors_bio_idx", "bio", IndexKind.Custom(SAI.dse)).toCQL shouldBe
    "CREATE CUSTOM INDEX authors_bio_idx ON blog.authors (bio) USING 'StorageAttachedIndex'"
  }

  it should "support IF NOT EXISTS combined with a custom index" in {
    CreateIndex(
      AuthorsTable,
      "authors_bio_idx",
      "bio",
      IndexKind.Custom(SAI.openSource)
    ).ifNotExists.toCQL shouldBe
    "CREATE CUSTOM INDEX IF NOT EXISTS authors_bio_idx ON blog.authors (bio) " +
    "USING 'org.apache.cassandra.index.sai.StorageAttachedIndex'"
  }

  "=== on a custom (SAI) indexed column" should "execute without allowFiltering, same as a secondary one" in {
    val cql = AuthorsTable.select().where(AuthorsTable.bio === "Scala enthusiast").toCQL
    cql shouldBe "SELECT * FROM blog.authors WHERE bio = 'Scala enthusiast'"
  }

  // ---- entry (ENTRIES index) ------------------------------------------------

  "entry on an indexed column" should "execute without allowFiltering" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.attributes.entry("color", "red"))
      .toCQL

    cql shouldBe "SELECT * FROM blog.profiles WHERE attributes['color'] = 'red'"
  }

  it should "combine with a full primary-key restriction and still execute" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.id === fixedId and ProfilesTable.attributes.entry("color", "red"))
      .toCQL

    cql should include(s"WHERE id = $fixedId AND attributes['color'] = 'red'")
  }

  "entry on a non-indexed column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """ProfilesTable.select().where(ProfilesTable.settings.entry("locale", "en")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = ProfilesTable
      .select()
      .where(ProfilesTable.settings.entry("locale", "en"))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.profiles WHERE settings['locale'] = 'en' ALLOW FILTERING"
  }

  // ---- compound: contains + containsKey + entry all indexed on ONE column --

  "A map column with all 3 indices" should "let contains, containsKey and entry each execute without allowFiltering" in {
    ProfilesTable.select().where(ProfilesTable.attributes.contains("red")).toCQL should
    include("attributes CONTAINS 'red'")
    ProfilesTable.select().where(ProfilesTable.attributes.containsKey("color")).toCQL should
    include("attributes CONTAINS KEY 'color'")
    ProfilesTable.select().where(ProfilesTable.attributes.entry("color", "red")).toCQL should
    include("attributes['color'] = 'red'")
  }
  it should "let all 3 predicates be combined in one WHERE and still execute without allowFiltering" in {
    // Verifies the type-level contributions compound correctly: each is
    // "free" (Eq = In = Rng = HNil), so `and`-ing three of them, plus a real
    // primary-key ===, still satisfies the ungated execute gate. NOTE: real
    // Cassandra is stricter than this, it only allows ONE index-driven
    // restriction without ALLOW FILTERING, so this exact shape is rejected
    // by the server (see IndexIntegrationSpec); this test is purely about
    // the compile-time HList merging, not a claim the query is efficient.
    val cql = ProfilesTable
      .select()
      .where(
        ProfilesTable.id === fixedId and
          ProfilesTable.attributes.contains("red") and
          ProfilesTable.attributes.containsKey("color") and
          ProfilesTable.attributes.entry("color", "red")
      )
      .toCQL

    cql shouldBe
    s"SELECT * FROM blog.profiles WHERE id = $fixedId " +
    "AND attributes CONTAINS 'red' AND attributes CONTAINS KEY 'color' AND attributes['color'] = 'red'"
  }

  // ---- Table.indexKeys / indexValuesAndKeys / etc.: pick which of a map's
  // ---- aspects are actually indexed --------------------------------------

  "Table.indexKeys" should "register only a KEYS index" in {
    DocumentsTable.createIndexes.filter(_.target.contains("tags")).map(_.toCQL) shouldBe
    Seq("CREATE INDEX documents_tags_keys_idx ON blog.documents (KEYS(tags))")
  }

  "Table.indexValuesAndKeys" should "register both a values and a KEYS index, but no ENTRIES index" in {
    DocumentsTable.createIndexes.filter(_.target.contains("metadata")).map(_.toCQL) shouldBe
    Seq(
      "CREATE INDEX documents_metadata_idx ON blog.documents (metadata)",
      "CREATE INDEX documents_metadata_keys_idx ON blog.documents (KEYS(metadata))"
    )
  }

  "containsKey on a Keys-only indexed column" should "execute without allowFiltering" in {
    val cql = DocumentsTable.select().where(DocumentsTable.tags.containsKey("color")).toCQL
    cql shouldBe "SELECT * FROM blog.documents WHERE tags CONTAINS KEY 'color'"
  }

  "contains on a Keys-only indexed column" should "NOT compile without allowFiltering (no values index exists)" in {
    assertTypeError(
      """DocumentsTable.select().where(DocumentsTable.tags.contains("red")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql =
      DocumentsTable.select().where(DocumentsTable.tags.contains("red")).allowFiltering.toCQL
    cql shouldBe "SELECT * FROM blog.documents WHERE tags CONTAINS 'red' ALLOW FILTERING"
  }

  "entry on a Keys-only indexed column" should "NOT compile without allowFiltering (no entries index exists)" in {
    assertTypeError(
      """DocumentsTable.select().where(DocumentsTable.tags.entry("color", "red")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = DocumentsTable
      .select()
      .where(DocumentsTable.tags.entry("color", "red"))
      .allowFiltering
      .toCQL
    cql shouldBe "SELECT * FROM blog.documents WHERE tags['color'] = 'red' ALLOW FILTERING"
  }

  "contains and containsKey on a ValuesAndKeys indexed column" should "both execute without allowFiltering" in {
    DocumentsTable.select().where(DocumentsTable.metadata.contains("red")).toCQL should
    include("metadata CONTAINS 'red'")
    DocumentsTable.select().where(DocumentsTable.metadata.containsKey("color")).toCQL should
    include("metadata CONTAINS KEY 'color'")
  }

  "entry on a ValuesAndKeys indexed column" should "NOT compile without allowFiltering (no entries index exists)" in {
    assertTypeError(
      """DocumentsTable.select().where(DocumentsTable.metadata.entry("color", "red")).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = DocumentsTable
      .select()
      .where(DocumentsTable.metadata.entry("color", "red"))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.documents WHERE metadata['color'] = 'red' ALLOW FILTERING"
  }

  "A partially-indexed map column" should "still work as an ordinary column (assignment, key)" in {
    val cql = DocumentsTable.insert
      .value(DocumentsTable.id := fixedId)
      .value(DocumentsTable.tags := Map("color" -> "red"))
      .toCQL

    cql should include("(id, tags) VALUES")
  }

  // ---- frozen maps: no contains / containsKey / entry at all, only === ----

  "Table.index" should "register a single FULL index for an indexed frozen map column" in {
    CatalogsTable.createIndexes.map(_.toCQL) shouldBe
    Seq("CREATE INDEX catalogs_labels_full_idx ON blog.catalogs (FULL(labels))")
  }

  it should "not register an index for a frozen map column that was not wrapped in index(...)" in {
    CatalogsTable.createIndexes.map(_.target) should not contain "tags"
  }

  "contains" should "NOT compile on a frozen map column, indexed or not" in {
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.labels.contains("red")).allowFiltering.toCQL"""
    )
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.tags.contains("red")).allowFiltering.toCQL"""
    )
  }

  "containsKey" should "NOT compile on a frozen map column either, indexed or not" in {
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.labels.containsKey("color")).allowFiltering.toCQL"""
    )
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.tags.containsKey("color")).allowFiltering.toCQL"""
    )
  }

  "entry" should "NOT compile on a frozen map column either, indexed or not" in {
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.labels.entry("color", "red")).allowFiltering.toCQL"""
    )
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.tags.entry("color", "red")).allowFiltering.toCQL"""
    )
  }

  "=== on an indexed frozen map column" should "execute without allowFiltering" in {
    val cql = CatalogsTable
      .select()
      .where(CatalogsTable.labels === Frozen(Map("color" -> "red")))
      .toCQL

    cql shouldBe "SELECT * FROM blog.catalogs WHERE labels = {'color':'red'}"
  }

  "=== on a non-indexed frozen map column" should "NOT compile without allowFiltering" in {
    assertTypeError(
      """CatalogsTable.select().where(CatalogsTable.tags === Frozen(Map("color" -> "red"))).toCQL"""
    )
  }

  it should "execute once allowFiltering is used" in {
    val cql = CatalogsTable
      .select()
      .where(CatalogsTable.tags === Frozen(Map("color" -> "red")))
      .allowFiltering
      .toCQL

    cql shouldBe "SELECT * FROM blog.catalogs WHERE tags = {'color':'red'} ALLOW FILTERING"
  }

  "A frozen map column" should "still work as an ordinary column (assignment, key)" in {
    val cql = CatalogsTable.insert
      .value(CatalogsTable.id := fixedId)
      .value(CatalogsTable.labels := Frozen(Map("color" -> "red")))
      .toCQL

    cql should include("(id, labels) VALUES")
  }
}
