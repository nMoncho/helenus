/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Failure
import scala.util.Success

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class ScalaPreparedStatementMappedSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._
  import ScalaPreparedStatementMappedSpec._

  private implicit lazy val cqlSession: CqlSession = session

  private implicit val adapter: Mapping[Hotel] = Mapping[Hotel]()

  private implicit val pagerSerializer: PagerSerializer[String] =
    PagerSerializer.DefaultPagingStateSerializer

  "ScalaPreparedStatementFrom" should {
    "prepare a query" in {
      val insert =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepareFrom[Hotel]

      insert.execute(Hotels.h1)
      insert.execute(Hotels.h1.copy(name = null))
      insert.execute(Hotels.h1.copy(pois = null))

      val query = """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL
        .prepare[String]
        .as[Hotel]

      query.execute(Hotels.h1.id).nextOption() shouldBe Some(Hotels.h1)

      val mappedQuery = """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL
        .prepareFrom[Hotel]
        .as[Hotel]

      mappedQuery.execute(Hotels.h1).nextOption() shouldBe Some(Hotels.h1)

      withClue("and handle not ignore null fields") {
        val insertWithNulls = insert.withIgnoreNullFields(ignore = false)

        insertWithNulls.execute(Hotels.h1)
        insertWithNulls.execute(Hotels.h1.copy(name = null))
        insertWithNulls.execute(Hotels.h1.copy(pois = null))
      }
    }

    "prepare a query (async)" in {
      val insert =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQLAsync
          .prepareFrom[Hotel]

      val mappedQuery =
        """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL.prepareFrom[Hotel].as[Hotel]

      val tx = for {
        _ <- insert.executeAsync(Hotels.h1)
        q <- mappedQuery.executeAsync(Hotels.h1)
        r <- q.nextOption()
      } yield r

      whenReady(tx) { result =>
        result shouldBe defined
        result.value._1 shouldBe Hotels.h1
      }
    }

    "propagate exceptions when preparing" in {
      // should propagate exceptions ('name' is not part of the PK)
      intercept[InvalidQueryException] {
        "SELECT * FROM hotels WHERE name = ?".toCQL.prepareFrom[Hotel]
      }
    }

    "propagate exceptions when preparing (async)" in {
      whenReady(
        "SELECT * FROM hotels WHERE name = ?".toCQLAsync.prepareFrom[Hotel].failed
      )(_ shouldBe a[InvalidQueryException])
    }

    "map results with an explicit RowMapper" in {
      insertHotels.execute(Hotels.h1)

      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]
        .as[Hotel](Hotel.rowMapper)

      query.execute(Hotels.h1).nextOption() shouldBe Some(Hotels.h1)
    }

    "keep options when mapping results" in {
      val pstmt = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]
        .withIgnoreNullFields(ignore = false)

      pstmt.options.ignoreNullFields shouldBe false
      pstmt.as[Hotel].options.ignoreNullFields shouldBe false

      withClue("and options can be replaced") {
        pstmt.withOptions(StatementOptions.default).options.ignoreNullFields shouldBe true
      }
    }

    "page a query" in {
      insertPois()

      val pageSize = 2
      val hotelId  = HotelId(Hotels.h1.id)
      val pstmt    = "SELECT poi_name, description FROM pois_by_hotel WHERE hotel_id = ?".toCQL
        .prepareFrom[HotelId]
        .as[(String, String)]

      val (pager1, page0) = pstmt.pager(hotelId).execute(pageSize)
      page0.toSeq should have size pageSize
      pager1.hasMorePages shouldBe true

      val (_, page1)   = pager1.execute(pageSize)
      val page1Results = page1.toSeq
      page1Results should have size pageSize

      withClue("resume from a paging state") {
        pstmt.pager(pager1.pagingState.value, hotelId) match {
          case Success(pager) =>
            pager.execute(pageSize)._2.toSeq shouldEqual page1Results

          case Failure(exception) =>
            fail("paging state should be accepted", exception)
        }
      }

      withClue("resume from a serialized paging state") {
        pstmt.pager(pager1.encodePagingState.value, hotelId) match {
          case Success(pager) =>
            pager.execute(pageSize)._2.toSeq shouldEqual page1Results

          case Failure(exception) =>
            fail("paging state should be decoded", exception)
        }
      }
    }

    "page a query (async)" in {
      insertPois()

      val pageSize = 2
      val pstmt    = "SELECT poi_name, description FROM pois_by_hotel WHERE hotel_id = ?".toCQLAsync
        .prepareFrom[HotelId]
        .as[(String, String)]

      val pager0          = whenReady(pstmt.pager(HotelId(Hotels.h1.id)))(identity)
      val (pager1, page0) = whenReady(pager0.executeAsync(pageSize))(identity)
      page0.toSeq should have size pageSize
      pager1.hasMorePages shouldBe true

      val (_, page1) = whenReady(pager1.executeAsync(pageSize))(identity)
      page1.toSeq should have size pageSize
    }

    "map results and set options (async)" in {
      val insert =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQLAsync
          .prepareFrom[Hotel]
          .withOptions(StatementOptions.default)

      val query = "SELECT * FROM hotels WHERE id = ?".toCQLAsync
        .prepareFrom[Hotel]
        .as[Hotel](Hotel.rowMapper)

      val tx = for {
        _ <- insert.executeAsync(Hotels.h1)
        q <- query.executeAsync(Hotels.h1)
        r <- q.nextOption()
      } yield r

      whenReady(tx) { result =>
        result shouldBe defined
        result.value._1 shouldBe Hotels.h1
      }
    }
  }

  private def insertHotels =
    """INSERT INTO hotels(id, name, phone, address, pois)
      |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
      .prepareFrom[Hotel]

  private def insertPois(): Unit = {
    val insert =
      """INSERT INTO pois_by_hotel(hotel_id, poi_name, description)
        |VALUES (?, ?, ?)""".stripMargin.toCQL
        .prepare[String, String, String]

    PointOfInterests.all.foreach(poi => insert.execute(Hotels.h1.id, poi.name, poi.description))
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
  }

}

object ScalaPreparedStatementMappedSpec {

  final case class HotelId(hotelId: String)

  object HotelId {
    implicit val namingScheme: ColumnNamingScheme = ColumnNamingScheme.SnakeCase

    implicit val mapping: Mapping[HotelId] = Mapping[HotelId]()
  }

}
