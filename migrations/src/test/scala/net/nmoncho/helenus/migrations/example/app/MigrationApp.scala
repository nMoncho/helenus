/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example.app

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

  /** Migration knobs, one per executor parameter: how finely to split each ring range,
    * how many ranges to read at once, an optional rate cap, and an optional read
    * execution profile. The library takes each of these as a plain method parameter,
    * so this config only exists in the example app.
    */
  final case class Config(
      splits: Int                      = 8,
      parallelism: Int                 = Runtime.getRuntime.availableProcessors,
      rateLimit: Option[RateLimit]     = None,
      executionProfile: Option[String] = None,
      dryRun: Boolean                  = false
  )

  object Config {

    /** Reads `token-range-splits` (required), `parallelism`, an optional
      * `throttling { elements, per }` block, an optional `read-execution-profile`, and
      * an optional `dry-run` flag.
      */
    def fromConfig(config: com.typesafe.config.Config): Config = {
      val defaults = Config()

      val rateLimit =
        if (config.hasPath("throttling")) {
          val throttling = config.getConfig("throttling")
          Some(RateLimit(throttling.getInt("elements"), throttling.getDuration("per").toScala))
        } else {
          None
        }

      Config(
        splits      = config.getInt("token-range-splits"),
        parallelism =
          if (config.hasPath("parallelism")) config.getInt("parallelism") else defaults.parallelism,
        rateLimit        = rateLimit,
        executionProfile =
          if (config.hasPath("read-execution-profile"))
            Some(config.getString("read-execution-profile"))
          else None,
        dryRun = config.hasPath("dry-run") && config.getBoolean("dry-run")
      )
    }
  }
}
