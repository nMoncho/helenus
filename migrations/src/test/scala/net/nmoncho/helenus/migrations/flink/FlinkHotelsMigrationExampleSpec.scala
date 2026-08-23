/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.flink

import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus._
import net.nmoncho.helenus.flink._
import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** The same neutral hotels-to-hotels_by_city denormalization as the Pekko
  * example, run as a Flink job through `asTokenRangeMigration`.
  */
class FlinkHotelsMigrationExampleSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with BeforeAndAfterEach {

  import FlinkHotelsMigrationExampleSpec.Hotel
  import FlinkHotelsMigrationExampleSpec.HotelByCity

  private val hotels = Seq(
    ("h1", "Grand", "Paris"),
    ("h2", "Plaza", "Paris"),
    ("h3", "Ritz", "London"),
    ("h4", "Savoy", "London"),
    ("h5", "Nord", "Berlin")
  )

  private lazy val flinkCluster = new MiniClusterWithClientResource(
    new MiniClusterResourceConfiguration.Builder()
      .setNumberSlotsPerTaskManager(2)
      .setNumberTaskManagers(1)
      .build
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
    flinkCluster.before()
    hotels.foreach { case (id, name, city) =>
      execute(s"INSERT INTO hotels (id, name, city) VALUES ('$id', '$name', '$city')")
    }
  }

  override def afterEach(): Unit =
    try flinkCluster.after()
    finally super.afterEach()

  "The Flink hotels-to-hotels_by_city example migration" should {

    "denormalize every hotel into the by-city table" in {
      val driverConfig = cassandraConfig

      val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)

      asTokenRangeMigration(
        env,
        read = (s: CqlSession) =>
          "SELECT id, name, city FROM hotels".toUnsafeCQL(s).prepareUnit.as[Hotel].apply(),
        transform = (hotel: Hotel) => HotelByCity(hotel.city, hotel.id, hotel.name),
        write     = (s: CqlSession) =>
          "INSERT INTO hotels_by_city (city, id, name) VALUES (?, ?, ?)"
            .toUnsafeCQL(s)
            .prepare[String, String, String]
            .from[HotelByCity],
        sourceConfig = CassandraSource.Config().copy(config = driverConfig),
        sinkConfig   = CassandraSink.Config().copy(config = driverConfig)
      )

      env.execute()

      val byCity = execute("SELECT city, id FROM hotels_by_city")
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

object FlinkHotelsMigrationExampleSpec {

  final case class Hotel(id: String, name: String, city: String) {
    def this() = this("", "", "")
  }

  object Hotel {
    // Hand-written so the source mapper is Flink-serializable (see FlinkMigrationSpec).
    implicit val rowMapper: RowMapper[Hotel] = new RowMapper[Hotel] {
      override def apply(row: Row): Hotel =
        Hotel(row.getCol[String]("id"), row.getCol[String]("name"), row.getCol[String]("city"))
    }

    implicit val typeInfo: TypeInformation[Hotel] = Pojo[Hotel]
  }

  final case class HotelByCity(city: String, id: String, name: String) {
    def this() = this("", "", "")
  }

  object HotelByCity {
    implicit val adapter: Adapter[HotelByCity, (String, String, String)] = Adapter[HotelByCity]
    implicit val typeInfo: TypeInformation[HotelByCity]                  = Pojo[HotelByCity]
  }
}
