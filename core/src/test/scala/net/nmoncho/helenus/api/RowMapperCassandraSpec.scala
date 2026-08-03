/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement1
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** End-to-end check that [[RowMapper]] maps a genuine driver [[Row]], as a
  * counterpart to the mock-based `RowMapperSpec`. The mapping semantics are
  * covered there; this only makes sure the same wiring works against a real
  * row and real driver codecs (query execution, paging and async mapping are
  * already covered by `ScalaPreparedStatementMappedSpec`).
  */
class RowMapperCassandraSpec extends AnyWordSpec with Matchers with CassandraSpec {

  import HotelsTestData._

  private implicit lazy val cqlSession: CqlSession = session

  "RowMapper" should {
    "map a real row to a case class using explicit mappers" in {
      val query = "SELECT id, name, phone, address, pois FROM hotels WHERE id = ?".toCQL
        .prepare[String]

      val byName = query.as((row: Row) =>
        Hotel(
          row.getCol[String]("id"),
          row.getCol[String]("name"),
          row.getCol[String]("phone"),
          row.getCol[Address]("address"),
          row.getCol[Set[String]]("pois")
        )
      )

      val byIndex = query.as((row: Row) =>
        Hotel(
          row.getCol[String](0),
          row.getCol[String](1),
          row.getCol[String](2),
          row.getCol[Address](3),
          row.getCol[Set[String]](4)
        )
      )

      val byDerivedMapper = query.as(_.as[Hotel])

      def assertQuery(pstmt: ScalaPreparedStatement1[String, Hotel]): Unit =
        pstmt.execute(Hotels.h1.id).nextOption() shouldBe Some(Hotels.h1)

      assertQuery(byName)
      assertQuery(byIndex)
      assertQuery(byDerivedMapper)
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    insertTestData()
  }

  override def afterEach(): Unit = {
    // Don't truncate keyspace
  }
}
