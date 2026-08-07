/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

import scala.util.Try

import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter

/** Compile-checked usage examples backing this module's README.
  *
  * The bodies mirror the README snippets; they are never executed, so they need no live Cassandra
  * connection. Keeping them here means the README examples cannot silently stop compiling.
  */
object DocExamples {

  /** A prepared `SELECT` becomes a `ZStream` over the `ZCqlSession` environment. */
  def read: ZCqlStream[Try[IceCream]] =
    "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCream].stream()

  /** A prepared `INSERT` becomes a `ZSink`; each `IceCream` supplies the bind parameters. */
  def write =
    "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
      .prepare[String, Int, Boolean]
      .from[IceCream]
      .sink()
}

final case class IceCream(name: String, numCherries: Int, cone: Boolean)

object IceCream {
  implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]()
  implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
    Adapter.builder[IceCream].build
}
