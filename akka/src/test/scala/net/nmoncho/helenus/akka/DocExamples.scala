/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.akka

import scala.concurrent.Future

import akka.Done
import akka.NotUsed
import akka.stream.alpakka.cassandra.CassandraWriteSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.datastax.oss.driver.api.core.CqlSession

/** Compile-checked usage examples backing this module's README.
  *
  * The bodies mirror the README snippets; they are never executed, so they need no live Cassandra
  * connection. Keeping them here means the README examples cannot silently stop compiling.
  */
object DocExamples {
  import net.nmoncho.helenus._

  // `CqlSession` prepares the statement; `CassandraSession` (from the Alpakka registry) runs the stream.
  /** A prepared `SELECT` becomes an Alpakka `Source`. */
  def read(implicit cqlSession: CqlSession, session: CassandraSession): Source[IceCream, NotUsed] =
    "SELECT * FROM ice_creams".toCQL.prepareUnit.as[IceCream].asReadSource()

  /** A prepared `INSERT` becomes a `Sink`; each `IceCream` supplies the bind parameters. */
  def write(
      writeSettings: CassandraWriteSettings
  )(implicit cqlSession: CqlSession, session: CassandraSession): Sink[IceCream, Future[Done]] =
    "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
      .prepare[String, Int, Boolean]
      .from[IceCream]
      .asWriteSink(writeSettings)
}

final case class IceCream(name: String, numCherries: Int, cone: Boolean)

object IceCream {
  import net.nmoncho.helenus._

  implicit val rowMapper: RowMapper[IceCream]                        = RowMapper[IceCream]()
  implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
    Adapter.builder[IceCream].build
}
