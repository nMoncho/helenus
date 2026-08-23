/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example

import scala.concurrent.Future
import scala.jdk.DurationConverters.JavaDurationOps

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.migrations.MigrationMetrics
import net.nmoncho.helenus.migrations.RateLimit
import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.scaladsl.RunnableGraph

/** Runner scaffolding for a migration job, kept in the examples rather than the
  * published library (which therefore takes on no HTTP-server dependency). A concrete
  * subclass just supplies the migration graph; this base wires the lifecycle: start
  * a liveness endpoint, run the migration with a config-driven rate limit and counting
  * metrics, report how many rows were extracted and loaded, and stop the endpoint when
  * the migration ends (whether it succeeds or fails).
  *
  * This replaces the PoC's `MigrationApp` / `SimpleMigrationApp` / `MultiMigrationApp`;
  * running several migrations is just running several graphs, or one graph fed from a
  * merged source.
  */
abstract class MigrationApp(config: MigrationApp.Config, healthCheck: HealthCheck)(
    implicit system: ActorSystem,
    session: CassandraSession
) {
  import system.dispatcher

  /** Builds the migration graph, wired with the configured rate limit and the metrics
    * the runner reads counts from. The `CqlSession` is the session's underlying driver
    * session, resolved once before the graph is built.
    */
  protected def migration(
      rateLimit: Option[RateLimit],
      metrics: MigrationMetrics
  )(implicit cql: CqlSession): RunnableGraph[Future[Done]]

  /** Runs the migration and returns `(extracted, loaded)` row counts. */
  def run(): Future[(Long, Long)] = {
    healthCheck.start()
    val metrics = MigrationMetrics.counting()

    session
      .underlying()
      .flatMap { implicit cql: CqlSession => migration(config.rateLimit, metrics).run() }
      .map(_ => metrics.extracted -> metrics.loaded)
      .andThen { case _ => healthCheck.stop() }
  }
}

object MigrationApp {

  /** Migration knobs: how finely to split each ring range, and an optional rate cap. */
  final case class Config(splits: Int, rateLimit: Option[RateLimit])

  object Config {

    /** Reads `token-range-splits` and an optional `throttling { elements, per }` block. */
    def fromConfig(config: com.typesafe.config.Config): Config = {
      val rateLimit =
        if (config.hasPath("throttling")) {
          val throttling = config.getConfig("throttling")
          Some(RateLimit(throttling.getInt("elements"), throttling.getDuration("per").toScala))
        } else {
          None
        }

      Config(splits = config.getInt("token-range-splits"), rateLimit = rateLimit)
    }
  }
}
