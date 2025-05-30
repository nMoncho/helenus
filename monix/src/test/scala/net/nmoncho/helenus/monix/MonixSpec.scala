/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.monix

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Consumer
import monix.reactive.Observable
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.api.cql.PagerSerializer
import net.nmoncho.helenus.monix.MonixSpec._
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class MonixSpec extends AnyWordSpec with Matchers with CassandraSpec with ScalaFutures {

  import net.nmoncho.helenus._

  private implicit lazy val cqlSession: CqlSession              = session
  private implicit val pagerSerializer: PagerSerializer[String] =
    PagerSerializer.DefaultPagingStateSerializer

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "Helenus" should {
    val pageSize = 2

    "work with Monix (sync)" in {
      val observable = "SELECT * FROM ice_creams".toCQL.prepareUnit.as[IceCream].asObservable()

      val consumer = "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .asConsumer()

      testStream(ijes, observable, consumer)(identity)

      withClue("work with interpolated queries") {
        val name  = "vanilla"
        val query = cql"SELECT * FROM ice_creams WHERE name = $name".as[IceCream].asObservable()

        whenReady(query.toListL.runToFuture) { results =>
          results should not be empty
        }
      }

      val queryName = "SELECT * FROM ice_creams WHERE name = ?".toCQL
        .prepare[String]
        .as[IceCream]
        .asObservable("vanilla")

      whenReady(queryName.toListL.runToFuture) { result =>
        result should not be empty
      }

      val queryNameAndCone =
        "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toCQL
          .prepare[String, Boolean]
          .as[IceCream]
          .asObservable("vanilla", true)

      whenReady(queryNameAndCone.toListL.runToFuture) { result =>
        result should not be empty
      }

      withClue("use reactive pagination") {
        val rows = "SELECT * FROM ice_creams".toCQL.prepareUnit
          .as[IceCream]
          .pager()

        val pager0 = whenReady(rows.asObservable(pageSize).toListL.runToFuture) { result =>
          result should have size pageSize
          result.last._1
        }

        val rows2 = "SELECT * FROM ice_creams".toCQL.prepareUnit
          .as[IceCream]
          .pager(pager0.encodePagingState.get)
          .get

        whenReady(rows2.asObservable(pageSize).toListL.runToFuture) { result =>
          result should have size 1
        }
      }
    }

    "work with Monix (async)" in {
      val observable = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit.as[IceCream].asObservable()

      val consumer = "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .asConsumer()

      testStream(ijes, observable, consumer)(identity)

      withClue("work with interpolated queries") {
        val name  = "vanilla"
        val query =
          cqlAsync"SELECT * FROM ice_creams WHERE name = $name".as[IceCream].asObservable()

        whenReady(query.toListL.runToFuture) { results =>
          results should not be empty
        }
      }

      val queryName = "SELECT * FROM ice_creams WHERE name = ?".toCQLAsync
        .prepare[String]
        .as[IceCream]
        .asObservable("vanilla")

      whenReady(queryName.toListL.runToFuture) { result =>
        result should not be empty
      }

      val queryNameAndCone =
        "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toCQLAsync
          .prepare[String, Boolean]
          .as[IceCream]
          .asObservable("vanilla", true)

      whenReady(queryNameAndCone.toListL.runToFuture) { result =>
        result should not be empty
      }

      withClue("and use an explicit RowMapper") {
        val query = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
          .as((row: Row) =>
            IceCream(
              row.getCol[String]("name"),
              row.getCol[Int]("numCherries"),
              row.getCol[Boolean]("cone")
            )
          )
          .asObservable()

        testStream(ijes, query, consumer)(identity)
      }

      withClue("use reactive pagination") {
        val rows = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
          .as[IceCream]
          .pager()

        val pager0 = whenReady(rows.asObservable(pageSize).toListL.runToFuture) { result =>
          result should have size pageSize
          result.last._1
        }

        val rows2 = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
          .as[IceCream]
          .pager(pager0.encodePagingState.get)

        whenReady(rows2.asObservable(pageSize).toListL.runToFuture) { result =>
          result should have size 1
        }
      }
    }
  }

  /** Inserts data with a sink, and reads it back with source to compare it
    */
  private def testStream[T, U](
      data: Seq[T],
      source: Observable[T],
      sink: Consumer[U, Unit]
  )(fn: T => U): Unit = {
    val tx = for {
      // Write to DB
      _ <- Observable.from(data).map(fn).consumeWith(sink)
      // Read from DB
      values <- source.consumeWith(Consumer.toList)
    } yield values

    whenReady(tx.runToFuture) { dbValues =>
      dbValues.toSet shouldBe data.toSet
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("""CREATE TABLE IF NOT EXISTS ice_creams(
                 |  name         TEXT PRIMARY KEY,
                 |  numCherries  INT,
                 |  cone         BOOLEAN
                 |)""".stripMargin)
  }
}

object MonixSpec {
  case class IceCream(name: String, numCherries: Int, cone: Boolean)
  object IceCream {
    import net.nmoncho.helenus._
    implicit val rowMapper: RowMapper[IceCream]                        = RowMapper[IceCream]
    implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
      Adapter.builder[IceCream].build
  }

  private val ijes = List(
    IceCream("vanilla", numCherries    = 2, cone  = true),
    IceCream("chocolate", numCherries  = 0, cone  = false),
    IceCream("the answer", numCherries = 42, cone = true)
  )
}
