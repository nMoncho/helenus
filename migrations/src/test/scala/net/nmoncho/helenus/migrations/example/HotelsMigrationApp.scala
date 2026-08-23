/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example

import scala.concurrent.Future

import _root_.net.nmoncho.helenus._
import _root_.net.nmoncho.helenus.migrations.pekko._
import _root_.net.nmoncho.helenus.pekko._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.RateLimit
import net.nmoncho.helenus.migrations.TokenRangePlanner
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.RunnableGraph

/** A concrete example runner: migrate `hotels` into `hotels_by_city`, wired as a job
  * with a liveness endpoint, config-driven rate limiting, and row counting.
  */
final class HotelsMigrationApp(config: MigrationApp.Config, healthCheck: HealthCheck)(
    implicit system: ActorSystem,
    session: CassandraSession
) extends MigrationApp(config, healthCheck) {

  import HotelsMigrationApp.Hotel
  import HotelsMigrationApp.HotelByCity

  override protected def migration(
      rateLimit: Option[RateLimit],
      metrics: MigrationMetrics
  )(implicit cql: CqlSession): RunnableGraph[Future[Done]] = {
    val plan = TokenRangePlanner.plan(splitsPerRange = config.splits)

    val load =
      "INSERT INTO hotels_by_city (city, id, name) VALUES (?, ?, ?)".toUnsafeCQL
        .prepare[String, String, String]
        .from[HotelByCity]
        .asWriteSink(CassandraWriteSettings.defaults)

    "SELECT id, name, city FROM hotels WHERE token(id) > ? AND token(id) <= ?".toUnsafeCQL
      .prepare[Token, Token]
      .as[Hotel]
      .asTokenRangeMigration(
        plan,
        transform = Flow[Hotel].map(hotel => HotelByCity(hotel.city, hotel.id, hotel.name)),
        sink      = load,
        rateLimit = rateLimit,
        metrics   = metrics
      )
  }
}

object HotelsMigrationApp {

  final case class Hotel(id: String, name: String, city: String)

  object Hotel {
    implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()
  }

  final case class HotelByCity(city: String, id: String, name: String)

  object HotelByCity {
    implicit val adapter: Adapter[HotelByCity, (String, String, String)] = Adapter[HotelByCity]
  }

  /** A runnable template. Not exercised by tests, since it connects to a real cluster
    * configured under `helenus-migration` in `application.conf`.
    */
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("hotels-migration")
    import system.dispatcher

    implicit val session: CassandraSession =
      CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

    val root        = system.settings.config.getConfig("helenus-migration")
    val config      = MigrationApp.Config.fromConfig(root)
    val healthCheck = HealthCheck(
      root.getString("health-check.host"),
      root.getInt("health-check.port"),
      root.getString("health-check.url")
    )

    new HotelsMigrationApp(config, healthCheck).run().onComplete { result =>
      result.fold(
        error => system.log.error(s"Migration failed: ${error.getMessage}"),
        counts => system.log.info(s"Migration done: extracted=${counts._1} loaded=${counts._2}")
      )
      val _ = system.terminate()
    }
  }
}
