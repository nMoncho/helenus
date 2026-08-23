/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example

import java.net.HttpURLConnection
import java.net.URL

import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/** The example runner app (with its liveness endpoint) runs a full migration end
  * to end.
  */
class MigrationAppExampleSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(20, Seconds))

  private implicit lazy val system: ActorSystem =
    ActorSystem("hotels-app-spec", cassandraConfig)

  private implicit lazy val as: CassandraSession =
    CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

  private val hotels = Seq(
    ("h1", "Grand", "Paris"),
    ("h2", "Plaza", "Paris"),
    ("h3", "Ritz", "London")
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
    hotels.foreach { case (id, name, city) =>
      execute(s"INSERT INTO hotels (id, name, city) VALUES ('$id', '$name', '$city')")
    }
  }

  override def afterAll(): Unit = {
    whenReady(system.terminate())(_ => ())
    super.afterAll()
  }

  "The example HealthCheck" should {

    "answer 200 on the liveness path" in {
      val healthCheck = HealthCheck("localhost", 0, "health")
      healthCheck.start()
      try {
        val connection = new URL(s"http://localhost:${healthCheck.boundPort}/health")
          .openConnection()
          .asInstanceOf[HttpURLConnection]

        connection.getResponseCode shouldBe 200
      } finally {
        healthCheck.stop()
      }
    }
  }

  "The example HotelsMigrationApp" should {

    "run a full migration and report extracted and loaded counts" in {
      val app = new HotelsMigrationApp(
        MigrationApp.Config(splits = 4, rateLimit = None),
        HealthCheck("localhost", 0, "health")
      )

      whenReady(app.run()) { case (extracted, loaded) =>
        extracted shouldBe hotels.size.toLong
        loaded shouldBe hotels.size.toLong

        val byCity = execute("SELECT city, id FROM hotels_by_city")
          .iterator()
          .asScala
          .toList
          .groupBy(_.getString("city"))
          .map { case (city, rows) => city -> rows.map(_.getString("id")).toSet }

        byCity shouldBe Map("Paris" -> Set("h1", "h2"), "London" -> Set("h3"))
      }
    }
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
