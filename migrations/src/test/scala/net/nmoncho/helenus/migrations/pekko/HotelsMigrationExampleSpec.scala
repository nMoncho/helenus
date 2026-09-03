/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.pekko

import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import _root_.net.nmoncho.helenus._
import _root_.net.nmoncho.helenus.pekko._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.TokenRangePlanner
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/** A neutral, realistic migration example. It denormalizes `hotels` (keyed by
  * id) into `hotels_by_city` (keyed by city) so the data can be queried by city, a
  * classic reason to migrate data between Cassandra tables. The whole ETL is one
  * `asTokenRangeMigration` call: extract with the token-range executor, reshape each
  * row, and load into the target.
  */
class HotelsMigrationExampleSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaFutures {

  import HotelsMigrationExampleSpec.Hotel
  import HotelsMigrationExampleSpec.HotelByCity

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("hotels-example-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val hotels = Seq(
    Hotel("h1", "Grand", "Paris"),
    Hotel("h2", "Plaza", "Paris"),
    Hotel("h3", "Ritz", "London"),
    Hotel("h4", "Savoy", "London"),
    Hotel("h5", "Nord", "Berlin")
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("CREATE TABLE IF NOT EXISTS hotels (id text PRIMARY KEY, name text, city text)")
    executeDDL(
      "CREATE TABLE IF NOT EXISTS hotels_by_city " +
        "(city text, id text, name text, PRIMARY KEY (city, id))"
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    hotels.foreach(h =>
      execute(s"INSERT INTO hotels (id, name, city) VALUES ('${h.id}', '${h.name}', '${h.city}')")
    )
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  "The hotels-to-hotels_by_city example migration" should {

    "denormalize every hotel into the by-city table" in withSession { implicit cql =>
      val plan    = TokenRangePlanner.plan(splitsPerRange = 4)
      val metrics = MigrationMetrics.counting()

      val load: Sink[HotelByCity, Future[Done]] =
        "INSERT INTO hotels_by_city (city, id, name) VALUES (?, ?, ?)".toUnsafeCQL
          .prepare[String, String, String]
          .from[HotelByCity]
          .asWriteSink(CassandraWriteSettings.defaults)

      val migration =
        "SELECT id, name, city FROM hotels WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
          .prepare[Token, Token]
          .as[Hotel]
          .asTokenRangeMigration(
            plan,
            transform = Flow[Hotel].map(h => HotelByCity(h.city, h.id, h.name)),
            sink      = load,
            metrics   = metrics
          )

      whenReady(migration.run()) { _ =>
        val byCity = cql
          .execute("SELECT city, id FROM hotels_by_city")
          .iterator()
          .asScala
          .toList
          .groupBy(_.getString("city"))
          .map { case (city, rows) => city -> rows.map(_.getString("id")).toSet }

        byCity shouldBe Map(
          "Paris" -> Set("h1", "h2"),
          "London" -> Set("h3", "h4"),
          "Berlin" -> Set("h5")
        )

        metrics.loaded shouldBe hotels.size.toLong
      }
    }
  }

  private def withSession(fn: CqlSession => Unit): Unit = {
    import system.dispatcher
    whenReady(as.underlying().map(fn))(_ => ())
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

object HotelsMigrationExampleSpec {

  final case class Hotel(id: String, name: String, city: String)

  object Hotel {
    implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()
  }

  final case class HotelByCity(city: String, id: String, name: String)

  object HotelByCity {
    implicit val adapter: Adapter[HotelByCity, (String, String, String)] = Adapter[HotelByCity]
  }
}
