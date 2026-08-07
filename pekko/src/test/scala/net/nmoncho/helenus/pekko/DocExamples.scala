/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.pekko

import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

/** Compile-checked usage examples backing this module's README.
  *
  * The bodies mirror the README snippets; they are never executed, so they need no live Cassandra
  * connection. Keeping them here means the README examples cannot silently stop compiling.
  */
object DocExamples {
  import net.nmoncho.helenus._

  // `CqlSession` prepares the statement; `CassandraSession` (from the Pekko registry) runs the stream.
  /** A prepared `SELECT` becomes a Pekko Connectors `Source`. */
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

  implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]()
  implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
    Adapter.builder[IceCream].build
}
