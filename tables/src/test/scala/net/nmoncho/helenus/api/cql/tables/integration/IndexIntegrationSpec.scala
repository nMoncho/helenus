/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables
package integration

import java.util.UUID

import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.tables.dml.Select
import org.scalatest.DoNotDiscover

/** `Table.index` end to end: the CREATE INDEX statements it registers are
  * executed against the embedded Cassandra, then `contains` / `containsKey` /
  * `entry` / `===` on the indexed columns are run for real without
  * `allowFiltering`, while the same predicates on non-indexed columns are
  * confirmed rejected by the server without it, matching the compile-time
  * gate.
  *
  * Known gate limitation (found here, like the clustering-IN + collection
  * projection case in SelectExecuteIntegrationSpec): Cassandra allows AT MOST
  * ONE index-driven restriction without ALLOW FILTERING, even alongside a
  * full primary key. The gate does not model that cap — every such predicate
  * is treated as unconditionally "free" — so combining two or more of them
  * compiles and passes the gate, but the server still rejects it; see the
  * dedicated test below. Confirmed on both Cassandra 3.11.19 and 5.0.6 (the
  * embedded version used here) — not a version-specific quirk.
  */
@DoNotDiscover
class IndexIntegrationSpec extends CassandraIntegrationSpec {
  import net.nmoncho.helenus._

  private val article1  = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val article2  = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
  private val profile1  = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
  private val snapshot1 = UUID.fromString("423e4567-e89b-12d3-a456-426614174000")
  private val snapshot2 = UUID.fromString("523e4567-e89b-12d3-a456-426614174000")
  private val customer1 = UUID.fromString("623e4567-e89b-12d3-a456-426614174000")
  private val customer2 = UUID.fromString("723e4567-e89b-12d3-a456-426614174000")
  private val document1 = UUID.fromString("823e4567-e89b-12d3-a456-426614174000")
  private val catalog1  = UUID.fromString("923e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()

    ArticlesTable.drop.ifExists.execute()
    ArticlesTable.create.execute()
    ArticlesTable.createIndexes.foreach(idx => execute(idx.toCQL))

    ProfilesTable.drop.ifExists.execute()
    ProfilesTable.create.execute()
    ProfilesTable.createIndexes.foreach(idx => execute(idx.toCQL))

    SnapshotsTable.drop.ifExists.execute()
    SnapshotsTable.create.execute()
    SnapshotsTable.createIndexes.foreach(idx => execute(idx.toCQL))

    CustomersTable.drop.ifExists.execute()
    CustomersTable.create.execute()
    CustomersTable.createIndexes.foreach(idx => execute(idx.toCQL))

    DocumentsTable.drop.ifExists.execute()
    DocumentsTable.create.execute()
    DocumentsTable.createIndexes.foreach(idx => execute(idx.toCQL))

    CatalogsTable.drop.ifExists.execute()
    CatalogsTable.create.execute()
    CatalogsTable.createIndexes.foreach(idx => execute(idx.toCQL))

    ArticlesTable
      .insertFrom(Article(article1, "Scala at scale", Set("scala", "cql"), Set("backend")))
      .execute()

    ArticlesTable
      .insertFrom(Article(article2, "Gardening tips", Set("gardening"), Set("lifestyle")))
      .execute()

    ProfilesTable
      .insertFrom(Profile(profile1, Map("color" -> "red"), Map("locale" -> "en")))
      .execute()

    SnapshotsTable
      .insertFrom(Snapshot(snapshot1, Frozen(Set("scala", "cql")), Frozen(Set("backend"))))
      .execute()
    SnapshotsTable
      .insertFrom(Snapshot(snapshot2, Frozen(Set("gardening")), Frozen(Set("lifestyle"))))
      .execute()

    CustomersTable.insertFrom(Customer(customer1, "alice@example.com", 30)).execute()
    CustomersTable.insertFrom(Customer(customer2, "bob@example.com", 40)).execute()

    DocumentsTable
      .insertFrom(Document(document1, Map("color" -> "red"), Map("color" -> "red")))
      .execute()

    CatalogsTable
      .insertFrom(Catalog(catalog1, Frozen(Map("color" -> "red")), Frozen(Map("color" -> "red"))))
      .execute()
  }

  // ---- contains: indexed column, no ALLOW FILTERING ------------------------

  "contains on an indexed column" should "run without allowFiltering and find the matching row" in {
    val result = rows(ArticlesTable.select().where(ArticlesTable.tags.contains("scala")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(article1)
  }

  it should "combine with the primary key and still run without allowFiltering" in {
    val result = rows(
      ArticlesTable
        .select()
        .where(ArticlesTable.id === article1 and ArticlesTable.tags.contains("scala"))
        .execute()
    )
    result should have size 1
  }

  it should "find nothing for a value no row has, without needing allowFiltering" in {
    rows(
      ArticlesTable.select().where(ArticlesTable.tags.contains("cooking")).execute()
    ) shouldBe empty
  }

  // ---- contains: non-indexed column, rejected without ALLOW FILTERING ------

  "contains on a non-indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      Select.render(
        ArticlesTable.select().where(ArticlesTable.categories.contains("backend")),
        allowFiltering = false,
        prepared       = false
      )
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      ArticlesTable
        .select()
        .where(ArticlesTable.categories.contains("backend"))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(article1)
  }

  // ---- containsKey: indexed map column, no ALLOW FILTERING ------------------

  "containsKey on an indexed column" should "run without allowFiltering and find the matching row" in {
    val result =
      rows(ProfilesTable.select().where(ProfilesTable.attributes.containsKey("color")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(profile1)
  }

  it should "combine with the primary key and still run without allowFiltering" in {
    val result = rows(
      ProfilesTable
        .select()
        .where(ProfilesTable.id === profile1 and ProfilesTable.attributes.containsKey("color"))
        .execute()
    )

    result should have size 1
  }

  // ---- containsKey: non-indexed map column, rejected without ALLOW FILTERING

  "containsKey on a non-indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      Select.render(
        ProfilesTable.select().where(ProfilesTable.settings.containsKey("locale")),
        allowFiltering = false,
        prepared       = false
      )
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      ProfilesTable
        .select()
        .where(ProfilesTable.settings.containsKey("locale"))
        .allowFiltering
        .execute()
    )

    result.map(_.get("id", classOf[UUID])) shouldBe List(profile1)
  }

  // ---- Frozen: FULL index lets equality run without ALLOW FILTERING --------

  "equality on a FULL-indexed Frozen column" should "run without ALLOW FILTERING and find the matching row" in {
    val result = rows(
      SnapshotsTable.select().where(SnapshotsTable.labels === Frozen(Set("scala", "cql"))).execute()
    )

    result.map(_.get("id", classOf[UUID])) shouldBe List(snapshot1)
  }

  it should "find nothing for a value no row has, without needing ALLOW FILTERING" in {
    rows(
      SnapshotsTable.select().where(SnapshotsTable.labels === Frozen(Set("cooking"))).execute()
    ) shouldBe empty
  }

  "equality on a non-indexed Frozen column" should "be rejected by Cassandra without ALLOW FILTERING" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      SnapshotsTable.select().where(SnapshotsTable.tags === Frozen(Set("backend"))),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      SnapshotsTable
        .select()
        .where(SnapshotsTable.tags === Frozen(Set("backend")))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(snapshot1)
  }

  // ---- equality on ANY indexed column, not just collections -----------------

  "equality on an indexed scalar column" should "run without ALLOW FILTERING and find the matching row" in {
    val result =
      rows(CustomersTable.select().where(CustomersTable.email === "alice@example.com").execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(customer1)
  }

  it should "combine with the primary key and still run without allowFiltering" in {
    val result = rows(
      CustomersTable
        .select()
        .where(CustomersTable.id === customer1 and CustomersTable.email === "alice@example.com")
        .execute()
    )
    result should have size 1
  }

  "equality on a non-indexed scalar column" should "be rejected by Cassandra without ALLOW FILTERING" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      CustomersTable.select().where(CustomersTable.age === 30),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result =
      rows(CustomersTable.select().where(CustomersTable.age === 30).allowFiltering.execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(customer1)
  }

  // ---- entry: indexed map column (ENTRIES index), no ALLOW FILTERING -------

  "entry on an indexed column" should "run without allowFiltering and find the matching row" in {
    val result =
      rows(ProfilesTable.select().where(ProfilesTable.attributes.entry("color", "red")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(profile1)
  }

  it should "find nothing when the key exists but the value doesn't match, without needing allowFiltering" in {
    rows(
      ProfilesTable.select().where(ProfilesTable.attributes.entry("color", "blue")).execute()
    ) shouldBe empty
  }

  // ---- entry: non-indexed map column, rejected without ALLOW FILTERING -----

  "entry on a non-indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      ProfilesTable.select().where(ProfilesTable.settings.entry("locale", "en")),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      ProfilesTable
        .select()
        .where(ProfilesTable.settings.entry("locale", "en"))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(profile1)
  }

  // ---- compound: contains + containsKey + entry, all indexed on ONE column -

  "a map column with all 3 indices" should "let contains, containsKey and entry each run without allowFiltering" in {
    rows(ProfilesTable.select().where(ProfilesTable.attributes.contains("red")).execute())
      .map(_.get("id", classOf[UUID])) shouldBe List(profile1)
    rows(ProfilesTable.select().where(ProfilesTable.attributes.containsKey("color")).execute())
      .map(_.get("id", classOf[UUID])) shouldBe List(profile1)
    rows(ProfilesTable.select().where(ProfilesTable.attributes.entry("color", "red")).execute())
      .map(_.get("id", classOf[UUID])) shouldBe List(profile1)
  }

  // Cassandra restriction the type gate does not model (confirmed on both
  // 3.11.19 and 5.0.6, so not version-specific): it allows AT MOST ONE
  // index-driven restriction (CONTAINS / CONTAINS KEY / entry / indexed ===)
  // without ALLOW FILTERING, even alongside a full primary key; combining
  // two or more of them (on the same column or different ones) needs ALLOW
  // FILTERING regardless. Our gate treats every such predicate as
  // unconditionally "free", so it admits `.execute` here where the server
  // does not — confirmed via raw toCQL below (see the analogous note in
  // SelectExecuteIntegrationSpec for the clustering-IN + collection-
  // projection limitation).
  it should "actually need ALLOW FILTERING once combined, even though the gate admits .execute" in {
    an[InvalidQueryException] should be thrownBy
    execute(
      ProfilesTable
        .select()
        .where(
          ProfilesTable.id === profile1 and
            ProfilesTable.attributes.contains("red") and
            ProfilesTable.attributes.containsKey("color") and
            ProfilesTable.attributes.entry("color", "red")
        )
        .toCQL
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      ProfilesTable
        .select()
        .where(
          ProfilesTable.id === profile1 and
            ProfilesTable.attributes.contains("red") and
            ProfilesTable.attributes.containsKey("color") and
            ProfilesTable.attributes.entry("color", "red")
        )
        .allowFiltering
        .execute()
    )
    result should have size 1
  }

  // ---- Table.indexKeys: only the KEYS index physically exists ---------------
  // `tags` was declared with indexKeys (keys-only): only containsKey should
  // work without ALLOW FILTERING against the real server; contains and entry
  // must be rejected, since no values / entries index was ever created for it.

  "containsKey on a keys-only indexed column" should "run without allowFiltering and find the matching row" in {
    val result =
      rows(DocumentsTable.select().where(DocumentsTable.tags.containsKey("color")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  "contains on a keys-only indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      DocumentsTable.select().where(DocumentsTable.tags.contains("red")),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      DocumentsTable.select().where(DocumentsTable.tags.contains("red")).allowFiltering.execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  "entry on a keys-only indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      DocumentsTable.select().where(DocumentsTable.tags.entry("color", "red")),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      DocumentsTable
        .select()
        .where(DocumentsTable.tags.entry("color", "red"))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  // ---- Table.indexValuesAndKeys: only VALUES + KEYS indexes exist ----------
  // `metadata` was declared with indexValuesAndKeys: contains and containsKey
  // both run without allowFiltering; entry (no entries index) is rejected.

  "contains on a values+keys indexed column" should "run without allowFiltering and find the matching row" in {
    val result =
      rows(DocumentsTable.select().where(DocumentsTable.metadata.contains("red")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  "containsKey on a values+keys indexed column" should "run without allowFiltering and find the matching row" in {
    val result =
      rows(DocumentsTable.select().where(DocumentsTable.metadata.containsKey("color")).execute())
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  "entry on a values+keys indexed column" should "be rejected by Cassandra without allowFiltering" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      DocumentsTable.select().where(DocumentsTable.metadata.entry("color", "red")),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      DocumentsTable
        .select()
        .where(DocumentsTable.metadata.entry("color", "red"))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(document1)
  }

  // ---- Frozen maps: only whole-value equality works, via a FULL index -----
  // contains / containsKey / entry aren't even expressible on a Frozen map
  // (compile-time rejection, see IndexSpec) — nothing to check against the
  // server for those. Only === is left, exactly like a frozen Set/List.

  "equality on a FULL-indexed Frozen map column" should "run without ALLOW FILTERING and find the matching row" in {
    val result = rows(
      CatalogsTable.select().where(CatalogsTable.labels === Frozen(Map("color" -> "red"))).execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(catalog1)
  }

  it should "find nothing for a value no row has, without needing ALLOW FILTERING" in {
    rows(
      CatalogsTable
        .select()
        .where(CatalogsTable.labels === Frozen(Map("color" -> "blue")))
        .execute()
    ) shouldBe empty
  }

  "equality on a non-indexed Frozen map column" should "be rejected by Cassandra without ALLOW FILTERING" in {
    an[InvalidQueryException] should be thrownBy
    Select.render(
      CatalogsTable.select().where(CatalogsTable.tags === Frozen(Map("color" -> "red"))),
      allowFiltering = false,
      prepared       = false
    )
  }

  it should "run once allowFiltering is used" in {
    val result = rows(
      CatalogsTable
        .select()
        .where(CatalogsTable.tags === Frozen(Map("color" -> "red")))
        .allowFiltering
        .execute()
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(catalog1)
  }
}
