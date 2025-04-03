/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import scala.annotation.nowarn
import scala.concurrent.duration.DurationInt

import com.datastax.oss.driver.api.core.CqlSession
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
class MappedAsyncPagingIterableOpsSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._

  import scala.collection.compat._
  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit lazy val cqlSession: CqlSession = session

  "MappedAsyncPagingIterableOps" should {
    import org.scalatest.OptionValues._

    "iterate results one at a time (sync)" in {
      whenReady(
        "SELECT * FROM hotels".toCQLAsync.prepareUnit
          .as[Hotel]
          .map(_.withPageSize(2))
          .executeAsync()
      ) { pi =>
        val (hotelA, iteratorA) = pi.nextOption(10.seconds).value
        Hotels.all should contain(hotelA)

        val (hotelB, iteratorB) = iteratorA.nextOption(10.seconds).value
        Hotels.all should contain(hotelB)
        iteratorA shouldBe iteratorB // should still be the same page

        val (hotelC, iteratorC) = iteratorB.nextOption(10.seconds).value
        Hotels.all should contain(hotelC)
        iteratorC should not be iteratorA // we should move on to the next page

        val (hotelD, iteratorD) = iteratorC.nextOption(10.seconds).value
        Hotels.all should contain(hotelD)

        val (hotelE, iteratorE) = iteratorD.nextOption(10.seconds).value
        Hotels.all should contain(hotelE)

        iteratorE.nextOption(10.seconds) shouldBe empty
      }
    }

    "fetch the first result (async)" in {
      val test = for {
        result <- "SELECT * FROM hotels LIMIT 1".toCQLAsync.prepareUnit
          .as[Hotel]
          .executeAsync()

        firstResult = result.oneOption
        nextResult  = result.oneOption
      } yield {
        firstResult shouldBe defined
        nextResult should not be defined

        ()
      }

      whenReady(test)(_ => ())
    }

    "iterate results one at a time (async)" in {
      val test = for {
        result <- "SELECT * FROM hotels".toCQLAsync.prepareUnit
          .as[Hotel]
          .map(_.withPageSize(2))
          .executeAsync()

        Some((hotelA, iteratorA)) <- result.nextOption()
        Some((hotelB, iteratorB)) <- iteratorA.nextOption()
        Some((hotelC, iteratorC)) <- iteratorB.nextOption()
        Some((hotelD, iteratorD)) <- iteratorC.nextOption()
        Some((hotelE, iteratorE)) <- iteratorD.nextOption()
        lastResult <- iteratorE.nextOption()

      } yield {
        Hotels.all should contain(hotelA)
        Hotels.all should contain(hotelB)
        Hotels.all should contain(hotelC)
        Hotels.all should contain(hotelD)
        Hotels.all should contain(hotelE)

        iteratorA shouldBe iteratorB // should still be the same page
        iteratorC should not be iteratorA // we should move on to the next page
        lastResult shouldBe empty
      }

      whenReady(test)(_ => ())
    }

    "convert to an iterator" in {
      whenReady(
        "SELECT * FROM hotels".toCQLAsync.prepareUnit
          .as[Hotel]
          .map(_.withPageSize(2))
          .executeAsync()
      ) { pi =>
        val iterator = pi.iter(20.seconds)

        iterator.toSet shouldEqual Hotels.all.toSet
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
