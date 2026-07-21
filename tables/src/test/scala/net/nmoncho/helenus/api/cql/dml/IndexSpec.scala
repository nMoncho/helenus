/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import TestValues.fixedId

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
    ArticlesTable.createIndexes.map(_.columnName) should not contain "categories"
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

  "Table.index" should "register a CREATE INDEX statement for an indexed map column" in {
    ProfilesTable.createIndexes.map(_.toCQL) shouldBe
    Seq("CREATE INDEX profiles_attributes_idx ON blog.profiles (attributes)")
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
}
