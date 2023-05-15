/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus
package internal.cql

import scala.annotation.nowarn

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

@nowarn("cat=unused-imports")
class ScalaPreparedStatementSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._
  import scala.collection.compat._ // Don't remove me

  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit lazy val cqlSession: CqlSession = session

  private implicit val hotelAdapter
      : Adapter[Hotel, (String, String, String, Address, Set[String])] = Adapter[Hotel]

  "ScalaPreparedStatement" should {
    "prepare a query" in {
      // single parameter query
      "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepare[String] shouldBe a[ScalaPreparedStatement[_, _]]

      // multiple parameter query
      "SELECT * FROM hotels_by_poi WHERE poi_name = ? AND hotel_id = ?".toCQL
        .prepare[String, String] shouldBe a[ScalaPreparedStatement[_, _]]

      // should propagate exceptions ('name' is not part of the PK)
      intercept[InvalidQueryException] {
        "SELECT * FROM hotels WHERE name = ?".toCQL
          .prepare[String] shouldBe a[ScalaPreparedStatement[_, _]]
      }
    }

    "prepare a query (async)" in {
      // single parameter query
      whenReady(
        "SELECT * FROM hotels WHERE id = ?".toCQL
          .prepareAsync[String]
      )(pstmt => pstmt shouldBe a[ScalaPreparedStatement[_, _]])

      // multiple parameter query
      whenReady(
        "SELECT * FROM hotels_by_poi WHERE poi_name = ? AND hotel_id = ?".toCQL
          .prepareAsync[String, String]
      )(pstmt => pstmt shouldBe a[ScalaPreparedStatement[_, _]])

      // should propagate exceptions ('name' is not part of the PK)
      whenReady(
        "SELECT * FROM hotels WHERE name = ?".toCQL
          .prepareAsync[String]
          .failed
      )(failure => failure shouldBe a[InvalidQueryException])
    }

    "work as a function producing BoundStatement" in {
      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepare[String]

      val queryH1 = query(Hotels.h1.id)
      queryH1 shouldBe a[BoundStatement]

      // with a different hotel
      val queryH2 = query(Hotels.h2.id)
      queryH2 shouldBe a[BoundStatement]

      withClue("and can be executed") {
        val h1It     = queryH1.execute()
        val h1RowOpt = Option(h1It.one())

        h1RowOpt shouldBe defined
        h1RowOpt.map(_.getString("name")) shouldBe Some(Hotels.h1.name)
      }
    }

    "execute (short-hand function)" in {
      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepare[String]

      val h2RowOpt = Option(query.execute(Hotels.h2.id).one())
      h2RowOpt.map(_.getString("name")) shouldBe Some(Hotels.h2.name)

      whenReady(
        query
          .executeAsync(Hotels.h2.id)
          .map(it => it.currPage.nextOption())
      ) { h2RowOpt =>
        h2RowOpt.map(_.getString("name")) shouldBe Some(Hotels.h2.name)
      }
    }

    "extract (or adapt) a case class instance" in {
      val insertHotel =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepare[String, String, String, Address, Set[String]]
          .from[Hotel]

      withClue("should execute") {
        insertHotel.execute(Hotels.h2)
      }
    }

    "extract (or adapt) a case class instance (async)" in {
      val insertHotel = """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
        .prepareAsync[String, String, String, Address, Set[String]]
        .from[Hotel]

      whenReady(insertHotel.executeAsync(Hotels.h2)) { _ =>
        // should execute
      }
    }

    "not set 'null' parameters" in {
      import scala.jdk.CollectionConverters._

      def checkIfPhoneIsSet(bs: BoundStatement, shouldBeSet: Boolean): Unit = {
        val columns = bs.getPreparedStatement.getVariableDefinitions.iterator().asScala.toList
        val unsetColumns = columns
          .filterNot(col => bs.isSet(col.getName))
          .map(_.getName.asInternal())

        if (shouldBeSet) {
          unsetColumns shouldBe empty
        } else {
          unsetColumns.headOption shouldBe Some("phone")
        }
      }

      val h2 = Hotels.h2
      val insertHotel =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepare[String, String, String, Address, Set[String]]

      withClue("when phone is set") {
        checkIfPhoneIsSet(
          insertHotel(h2.id, h2.name, h2.phone, h2.address, h2.pois),
          shouldBeSet = true
        )
      }

      withClue("when phone is not set") {
        checkIfPhoneIsSet(
          insertHotel(h2.id, h2.name, null, h2.address, h2.pois),
          shouldBeSet = false
        )
      }

      val insertHotelOpt =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepare[String, String, Option[String], Address, Set[String]]

      withClue("when phone is set") {
        checkIfPhoneIsSet(
          insertHotelOpt(h2.id, h2.name, Some(h2.phone), h2.address, h2.pois),
          shouldBeSet = true
        )
      }

      withClue("when phone is not set") {
        checkIfPhoneIsSet(
          insertHotelOpt(h2.id, h2.name, None, h2.address, h2.pois),
          shouldBeSet = false
        )
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
