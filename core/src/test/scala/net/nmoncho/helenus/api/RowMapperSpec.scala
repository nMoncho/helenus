/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api

import scala.annotation.nowarn

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement1
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

@nowarn("cat=unused-imports")
class RowMapperSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._

  import scala.collection.compat._
  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit lazy val cqlSession: CqlSession = session

  // We create the mapper here to avoid testing the generic derivation
  implicit val rowMapper: RowMapper[Hotel] = (row: Row) =>
    Hotel(
      row.getCol[String]("id"),
      row.getCol[String]("name"),
      row.getCol[String]("phone"),
      Address.Empty,
      Set.empty[String]
    )

  "RowMapper" should {
    "map rows" in {
      // this test if when users don't use the short-hand syntax
      val query = "SELECT name FROM hotels WHERE id = ?".toCQL
        .prepare[String]
        .as[String]

      query(Hotels.h3.id)
        .execute()
        .nextOption() shouldBe Some(Hotels.h3.name)

      query(Hotels.h4.id)
        .execute()
        .nextOption() shouldBe Some(Hotels.h4.name)

      val page = whenReady(query(Hotels.h5.id).executeAsync()) { page =>
        page.currPage.nextOption() shouldBe Some(Hotels.h5.name)

        page
      }

      whenReady(page.nextPage) { next =>
        next shouldBe empty
      }

      withClue(", with an Either field") {
        case class Hotel2(
            id: String,
            name: String,
            phoneOrAddress: Either[String, Address],
            pois: Set[String]
        )

        implicit val phoneOrAddressColMapper: ColumnMapper[Either[String, Address]] =
          ColumnMapper.either[String, Address]("phone", "address")
        implicit val mapper: RowMapper[Hotel2] = RowMapper[Hotel2]

        val query = "SELECT * FROM hotels WHERE id = ?".toCQL
          .prepare[String]
          .as[Hotel2]

        query(Hotels.h3.id)
          .execute()
          .nextOption()
          .value
          .phoneOrAddress shouldBe Right(Hotels.h3.address)
      }
    }

    "map single column results" in {
      val query = "SELECT name FROM hotels WHERE id = ?".toCQL
        .prepare[String]
        .as[String]

      val hotelH1Opt = query.execute(Hotels.h1.id).nextOption()
      hotelH1Opt shouldBe defined
      hotelH1Opt shouldBe Some(Hotels.h1.name)
    }

    "map result to tuples" in {
      val query = "SELECT name, phone FROM hotels WHERE id = ?".toCQL
        .prepare[String]
        .as[(String, String)]

      val hotelH1Opt = query.execute(Hotels.h1.id).nextOption()
      hotelH1Opt shouldBe defined
      hotelH1Opt shouldBe Some(Hotels.h1.name -> Hotels.h1.phone)
    }

    "map result to case classes" in {
      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepare[String]
        .as[Hotel]

      val hotelH1Opt = query.execute(Hotels.h1.id).nextOption()
      hotelH1Opt shouldBe defined
      hotelH1Opt.map(_.name) shouldBe Some(Hotels.h1.name)

      withClue("(when using an explicit mapper)") {
        def assertQuery(pstmt: ScalaPreparedStatement1[String, Hotel]) = {
          val hotelH1Opt = pstmt.execute(Hotels.h1.id).nextOption()
          hotelH1Opt shouldBe defined
          hotelH1Opt.map(_.name) shouldBe Some(Hotels.h1.name)
        }

        val query = "SELECT id, name, phone, address, pois FROM hotels WHERE id = ?".toCQL
          .prepare[String]

        val withNameGetCol = query.as((row: Row) =>
          Hotel(
            row.getCol[String]("id"),
            row.getCol[String]("name"),
            row.getCol[String]("phone"),
            row.getCol[Address]("address"),
            row.getCol[Set[String]]("pois")
          )
        )

        val withIndexGetCol = query.as((row: Row) =>
          Hotel(
            row.getCol[String](0),
            row.getCol[String](1),
            row.getCol[String](2),
            row.getCol[Address](3),
            row.getCol[Set[String]](4)
          )
        )

        val withAs = query.as(_.as[Hotel])

        assertQuery(withNameGetCol)
        assertQuery(withIndexGetCol)
        assertQuery(withAs)
      }
    }

    "map result to case classes (async)" in {
      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepare[String]
        .as[Hotel]

      whenReady(
        query
          .executeAsync(Hotels.h2.id)
          .map(it => it.currPage.nextOption())
      ) { h2RowOpt =>
        h2RowOpt.map(_.name) shouldBe Some(Hotels.h2.name)
      }
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    insertTestData()
  }

  override def afterEach(): Unit = {
    // Don't truncate keyspace
  }
}
