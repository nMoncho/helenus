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
package api.cql

import java.nio.ByteBuffer
import java.time.LocalDate

import scala.util.Failure
import scala.util.Success

import com.datastax.oss.driver.api.core.CqlSession
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

class PagerSerializerSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._

  private implicit lazy val cqlSession: CqlSession = session

  private val pageSize      = 2
  private val tamperedState = "29a1f5e96cfd7e7f42fbb3b3092a"

  "DefaultPagingStateSerializer" should {
    implicit val pagerSerializer: PagerSerializer[String] =
      PagerSerializer.DefaultPagingStateSerializer

    validatePageSerializer(tamperedState = tamperedState, isSafe = true)
  }

  "SimplePagingStateSerializer" should {
    implicit val pagerSerializer: PagerSerializer[ByteBuffer] =
      PagerSerializer.SimplePagingStateSerializer

    validatePageSerializer(
      tamperedState = ByteBuffer.wrap(tamperedState.getBytes()),
      isSafe        = false
    )
  }

  private def validatePageSerializer[A](
      tamperedState: A,
      isSafe: Boolean
  )(implicit ser: PagerSerializer[A]): Unit = {

    "serialize state" in {
      val queryHotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
      val pager0      = queryHotels.pager()

      withClue("an initial page shouldn't have a paging state") {
        pager0.encodePagingState shouldBe empty
      }

      val (pager1, _) = pager0.execute(pageSize)
      val pager1PS    = pager1.encodePagingState

      val continuePager1 = withClue("a non initial page should have a valid paging state") {
        pager1PS should not be empty
        queryHotels.pager(pager1PS.value) match {
          case Success(value) =>
            value

          case Failure(exception) =>
            fail("Couldn't serialize a paging state", exception)
        }
      }

      withClue("Executing a Next Page, and Continuing a Page should provide the same results") {
        val (_, nextPage)     = pager1.execute(pageSize)
        val (_, continuePage) = continuePager1.execute(pageSize)

        nextPage.toSeq shouldEqual continuePage.toSeq
      }
    }

    "react to tampered state" in runIfSafe {
      val queryHotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]

      queryHotels.pager(tamperedState) match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Cannot deserialize paging state, invalid format. The serialized form was corrupted, or not initially generated from a PagingState object"
          )
      }
    }

    "react to a state from another statement" in runIfSafe {
      val queryHotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
      val queryRooms =
        "SELECT date, room_number, is_available FROM available_rooms_by_hotel_date WHERE hotel_id = ?".toCQL
          .prepare[String]
          .as[(LocalDate, Short, Boolean)]

      val (hotelsPager1, _) = queryHotels.pager().execute(pageSize)
      val (roomsPager1, _)  = queryRooms.pager(Hotels.h1.id).execute(pageSize)

      val queryHotelPage1PS = hotelsPager1.encodePagingState
      val queryRoomsPage1PS = roomsPager1.encodePagingState

      // use 'QueryRoom' State with 'QueryHotels' Statement
      queryHotels.pager(queryRoomsPage1PS.value) match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Either Query String and/or Bound Parameters don't match PagingState and cannot be reused with current state"
          )
      }

      // use 'QueryHotels' State with 'QueryRoom' Statement
      queryRooms.pager(queryHotelPage1PS.value, Hotels.h1.id) match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Either Query String and/or Bound Parameters don't match PagingState and cannot be reused with current state"
          )
      }
    }

    "react to state from same statement but different parameters" in runIfSafe {
      val queryRooms =
        "SELECT date, room_number, is_available FROM available_rooms_by_hotel_date WHERE hotel_id = ?".toCQL
          .prepare[String]
          .as[(LocalDate, Short, Boolean)]

      val (hotel1RoomsPager1, _) = queryRooms.pager(Hotels.h1.id).execute(pageSize)
      val (hotel2RoomsPager1, _) = queryRooms.pager(Hotels.h2.id).execute(pageSize)

      val hotel1PS = hotel1RoomsPager1.encodePagingState
      val hotel2PS = hotel2RoomsPager1.encodePagingState

      queryRooms.pager(hotel1PS.value, Hotels.h2.id) match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Either Query String and/or Bound Parameters don't match PagingState and cannot be reused with current state"
          )
      }

      queryRooms.pager(hotel2PS.value, Hotels.h1.id) match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Either Query String and/or Bound Parameters don't match PagingState and cannot be reused with current state"
          )
      }
    }

    def runIfSafe(fn: => Any): Any = if (isSafe) fn else ()
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
