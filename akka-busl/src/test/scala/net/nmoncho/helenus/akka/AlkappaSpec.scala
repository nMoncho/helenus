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

package net.nmoncho.helenus.akka

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.CassandraWriteSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.scaladsl.FlowWithContext
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.api.cql.Pager
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class AlkappaSpec extends AnyWordSpec with Matchers with CassandraSpec with ScalaFutures {

  import AlkappaSpec._
  import net.nmoncho.helenus._

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem(
      "alpakka-spec",
      cassandraConfig
    )

  private implicit lazy val as: CassandraSession = CassandraSessionRegistry(system)
    .sessionFor(CassandraSessionSettings())

  "Helenus" should {
    import system.dispatcher

    "work with Akka Streams (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSink(writeSettings)

      testStream(ijes, query, insert)(identity)

      val queryName: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams WHERE name = ?".toCQL
        .prepare[String]
        .as[IceCream]
        .asReadSource("vanilla")

      whenReady(queryName.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }

      val queryNameAndCone: Source[IceCream, NotUsed] =
        "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toCQL
          .prepare[String, Boolean]
          .as[IceCream]
          .asReadSource("vanilla", true)

      whenReady(queryNameAndCone.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }

      withClue("use reactive pagination") {
        val rows = Source.fromPublisher(
          "SELECT * FROM ice_creams".toCQL.prepareUnit
            .as[IceCream]
            .pager()
            .executeReactive(2)
        )

        val pager0 = whenReady(rows.runWith(Sink.seq[(Pager[IceCream], IceCream)])) { result =>
          result should have size 2
          result.last._1
        }

        val rows2 = Source.fromPublisher(
          "SELECT * FROM ice_creams".toCQL.prepareUnit
            .as[IceCream]
            .pager(pager0.encodePagingState.get)
            .get
            .executeReactive(2)
        )

        whenReady(rows2.runWith(Sink.seq[(Pager[IceCream], IceCream)])) { result =>
          result should have size 1
        }
      }
    }

    "work with Akka Streams and Context (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteFlowWithContext[String](writeSettings)

      testStreamWithContext(ijes, query, insert)(ij => ij -> ij.name)
    }

    "perform batched writes with Akka Stream (sync)" in withSession { implicit session =>
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQL.prepareUnit
        .as[IceCream]
        .asReadSource()

      val batchedInsert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSinkBatched(writeSettings, _.name.charAt(0))

      testStream(batchIjs, query, batchedInsert)(identity)
    }

    "work with Akka Streams (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSink(writeSettings)

      testStream(ijes, query, insert)(identity)

      withClue("and use an explicit RowMapper") {
        val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
          .as((row: Row) =>
            IceCream(
              row.getCol[String]("name"),
              row.getCol[Int]("numCherries"),
              row.getCol[Boolean]("cone")
            )
          )
          .asReadSource()

        testStream(ijes, query, insert)(identity)
      }
    }

    "work with Akka Streams and Context (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
        .as[IceCream]
        .asReadSource()

      val insert =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteFlowWithContext[String](writeSettings)

      testStreamWithContext(ijes, query, insert)(ij => ij -> ij.name)

      val queryName: Source[IceCream, NotUsed] =
        "SELECT * FROM ice_creams WHERE name = ?".toCQLAsync
          .prepare[String]
          .as[IceCream]
          .asReadSource("vanilla")

      whenReady(queryName.runWith(Sink.seq[IceCream])) { result =>
        result should not be empty
      }
    }

    "perform batched writes with Akka Stream (async)" in {
      val query: Source[IceCream, NotUsed] = "SELECT * FROM ice_creams".toCQLAsync.prepareUnit
        .as[IceCream]
        .asReadSource()

      val batchedInsert: Sink[IceCream, Future[Done]] =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .asWriteSinkBatched(writeSettings, _.name.charAt(0))

      testStream(batchIjs, query, batchedInsert)(identity)
    }
  }

  private def withSession(fn: CqlSession => Unit)(implicit ec: ExecutionContext): Unit =
    whenReady(as.underlying().map(fn))(_ => /* Do nothing, test should be inside */ ())

  /** Inserts data with a sink, and reads it back with source to compare it
    */
  private def testStream[T, U](
      data: immutable.Iterable[T],
      source: Source[T, NotUsed],
      sink: Sink[U, Future[Done]]
  )(fn: T => U): Unit = {
    import system.dispatcher

    val tx = for {
      // Write to DB
      _ <- Source(data).map(fn).runWith(sink)
      // Read from DB
      values <- source.runWith(Sink.seq)
    } yield values

    whenReady(tx) { dbValues =>
      dbValues.toSet shouldBe data.toSet
    }
  }

  /** Inserts data with a sink, and reads it back with source to compare it
    */
  private def testStreamWithContext[T, U, Ctx](
      data: immutable.Iterable[T],
      source: Source[T, NotUsed],
      flowWithContext: FlowWithContext[U, Ctx, U, Ctx, NotUsed]
  )(fn: T => (U, Ctx)): Unit = {
    import system.dispatcher

    val tx = for {
      // Write to DB
      _ <- Source(data).map(fn).via(flowWithContext).runWith(Sink.ignore)
      // Read from DB
      values <- source.runWith(Sink.seq)
    } yield values

    whenReady(tx) { dbValues =>
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

  private def cassandraConfig: Config = ConfigFactory
    .parseString(s"""
                    |datastax-java-driver.basic {
                    |  contact-points = ["$contactPoint"]
                    |  session-keyspace = "$keyspace"
                    |  load-balancing-policy.local-datacenter = "datacenter1"
                    |}""".stripMargin)
    .withFallback(ConfigFactory.load())
}

object AlkappaSpec {

  case class IceCream(name: String, numCherries: Int, cone: Boolean)
  object IceCream {
    import net.nmoncho.helenus._
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
    implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
      Adapter.builder[IceCream].build
  }

  private val writeSettings = CassandraWriteSettings.defaults

  private val ijes = List(
    IceCream("vanilla", numCherries    = 2, cone  = true),
    IceCream("chocolate", numCherries  = 0, cone  = false),
    IceCream("the answer", numCherries = 42, cone = true)
  )

  private val batchIjs = (0 until writeSettings.maxBatchSize).map { i =>
    val original = ijes(i % ijes.size)
    original.copy(name = s"${original.name} $i")
  }.toSet
}
