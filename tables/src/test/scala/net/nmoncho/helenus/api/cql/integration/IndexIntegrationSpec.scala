/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package integration

import java.util.UUID

import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.dml.Select
import org.scalatest.DoNotDiscover

/** `Table.index` end to end: the CREATE INDEX statements it registers are
  * executed against the embedded Cassandra, then `contains` / `containsKey`
  * on the indexed columns are run for real without `allowFiltering`, while
  * the same predicates on non-indexed columns are confirmed rejected by the
  * server without it, matching the compile-time gate. The `Frozen` FULL-index
  * case is checked the same way, but via raw `toCQL`: the compile-time gate
  * doesn't exempt equality on an indexed `Frozen` column (see IndexSpec), so
  * this is the only way to confirm the FULL index genuinely lets Cassandra
  * run it without ALLOW FILTERING.
  */
@DoNotDiscover
class IndexIntegrationSpec extends CassandraIntegrationSpec {
  import net.nmoncho.helenus._

  private val article1  = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val article2  = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
  private val profile1  = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")
  private val snapshot1 = UUID.fromString("423e4567-e89b-12d3-a456-426614174000")
  private val snapshot2 = UUID.fromString("523e4567-e89b-12d3-a456-426614174000")

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
  // The compile-time gate stays conservative for Frozen columns (IndexSpec),
  // so these run the raw toCQL directly to prove the FULL index is real.

  "equality on a FULL-indexed Frozen column" should "run without ALLOW FILTERING and find the matching row" in {
    val result = rows(
      Select.render(
        SnapshotsTable.select().where(SnapshotsTable.labels === Frozen(Set("scala", "cql"))),
        allowFiltering = false,
        prepared       = false
      )
    )

    result.map(_.get("id", classOf[UUID])) shouldBe List(snapshot1)
  }

  it should "find nothing for a value no row has, without needing ALLOW FILTERING" in {
    rows(
      Select.render(
        SnapshotsTable.select().where(SnapshotsTable.labels === Frozen(Set("cooking"))),
        allowFiltering = false,
        prepared       = false
      )
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
        .execute
    )
    result.map(_.get("id", classOf[UUID])) shouldBe List(snapshot1)
  }
}
