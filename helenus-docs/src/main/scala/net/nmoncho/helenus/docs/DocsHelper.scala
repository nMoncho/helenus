/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.docs

import com.datastax.oss.driver.api.core.CqlSession
import org.cassandraunit.utils.EmbeddedCassandraServerHelper

object DocsHelper {

  final val keyspace: String = "docs"

  private var started = false

  def startTimeout: Long = EmbeddedCassandraServerHelper.DEFAULT_STARTUP_TIMEOUT * 3

  private def startCassandra(): Unit = {
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(startTimeout)

    val session = EmbeddedCassandraServerHelper.getSession
    session.execute(
      s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 1}"
    )
    session.execute(s"USE $keyspace")
    session.execute(
      """CREATE TYPE address (
        |    street              TEXT,
        |    city                TEXT,
        |    state_or_province   TEXT,
        |    postal_code         TEXT,
        |    country             TEXT
        |)""".stripMargin
    )
    session.execute(
      """CREATE TABLE hotels (
         |    id          TEXT PRIMARY KEY,
         |    name        TEXT,
         |    phone       TEXT,
         |    address     FROZEN<address>,
         |    pois        SET<TEXT>
         |) WITH comment = 'Q2. Find information about a hotel'""".stripMargin
    )

    session.execute(
      """INSERT INTO hotels(id, name, phone, address, pois)
        |VALUES ('h1', 'The New York Hotel Rotterdam', '+31 10 217 3000', {street: 'Meent 78-82', city: 'Rotterdam', state_or_province: 'Zuid-Holland', postal_code: '3011 JM', country: 'Netherlands'}, {'Erasmus Bridge', 'Rotterdam Zoo', 'Markthal Rotterdam'})""".stripMargin
    )
  }

  def cqlSession: CqlSession = this.synchronized {
    if (!started) {
      startCassandra()
      started = true
    }

    EmbeddedCassandraServerHelper.getSession
  }

}
