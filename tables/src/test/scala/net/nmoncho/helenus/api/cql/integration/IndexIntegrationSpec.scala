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
  * server without it, matching the compile-time gate.
  */
@DoNotDiscover
class IndexIntegrationSpec extends CassandraIntegrationSpec {
  import net.nmoncho.helenus._

  private val article1 = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
  private val article2 = UUID.fromString("223e4567-e89b-12d3-a456-426614174000")
  private val profile1 = UUID.fromString("323e4567-e89b-12d3-a456-426614174000")

  override def beforeAll(): Unit = {
    super.beforeAll()

    ArticlesTable.drop.ifExists.execute()
    ArticlesTable.create.execute()
    ArticlesTable.createIndexes.foreach(idx => execute(idx.toCQL))

    ProfilesTable.drop.ifExists.execute()
    ProfilesTable.create.execute()
    ProfilesTable.createIndexes.foreach(idx => execute(idx.toCQL))

    ArticlesTable
      .insertFrom(Article(article1, "Scala at scale", Set("scala", "cql"), Set("backend")))
      .execute()

    ArticlesTable
      .insertFrom(Article(article2, "Gardening tips", Set("gardening"), Set("lifestyle")))
      .execute()

    ProfilesTable
      .insertFrom(Profile(profile1, Map("color" -> "red"), Map("locale" -> "en")))
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
}
