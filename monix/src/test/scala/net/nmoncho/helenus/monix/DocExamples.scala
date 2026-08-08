/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.monix

import com.datastax.oss.driver.api.core.CqlSession
import monix.reactive.Consumer
import monix.reactive.Observable

/** Compile-checked usage examples backing this module's README.
  *
  * The bodies mirror the README snippets; they are never executed, so they need no live Cassandra
  * connection. Keeping them here means the README examples cannot silently stop compiling.
  */
object DocExamples {
  import net.nmoncho.helenus._

  /** A prepared `SELECT` becomes an `Observable`. */
  def read(implicit session: CqlSession): Observable[IceCream] =
    "SELECT * FROM ice_creams".toCQL.prepareUnit.as[IceCream].asObservable()

  /** A prepared `INSERT` becomes a `Consumer`; each `IceCream` supplies the bind parameters. */
  def write(implicit session: CqlSession): Consumer[IceCream, Unit] =
    "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
      .prepare[String, Int, Boolean]
      .from[IceCream]
      .asConsumer()
}

final case class IceCream(name: String, numCherries: Int, cone: Boolean)

object IceCream {
  import net.nmoncho.helenus._

  implicit val rowMapper: RowMapper[IceCream]                        = RowMapper[IceCream]()
  implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
    Adapter.builder[IceCream].build
}
