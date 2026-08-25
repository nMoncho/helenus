/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark

import java.net.URL

import com.datastax.spark.connector.cql.CassandraConnector
import net.nmoncho.helenus._
import org.apache.spark.SparkConf
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Regression guard for the java-driver.
  *
  * Helenus core depends on the unshaded `org.apache.cassandra:java-driver-core`, while the
  * spark-cassandra-connector ships the shaded `java-driver-core-shaded`. Both carry the same
  * un-relocated `com.datastax.oss.driver.api.core.*` API, so having both on one classpath
  * means duplicate classes and a load-order-dependent winner — a real
  * `LinkageError` / `NoSuchMethodError` risk. The connector's shaded driver
  * as the single provider; this spec fails loudly if a future dependency bump silently
  * reintroduces a second one.
  */
final class DriverClasspathSpec extends AnyWordSpec with Matchers {

  private val cqlSessionResource: String = "com/datastax/oss/driver/api/core/CqlSession.class"

  private def cqlSessionProviders: List[URL] = {
    val builder     = List.newBuilder[URL]
    val enumeration = getClass.getClassLoader.getResources(cqlSessionResource)
    while (enumeration.hasMoreElements) builder += enumeration.nextElement()
    builder.result()
  }

  "The java driver on the test classpath" should {
    "provide exactly one com.datastax.oss.driver.api.core.CqlSession" in {
      val providers = cqlSessionProviders

      withClue(s"CqlSession providers found: ${providers.mkString(", ")} — ") {
        providers should have size 1
      }
    }

    "resolve that single provider from the connector's shaded driver (D2 Option 1)" in {
      cqlSessionProviders match {
        case url :: Nil => url.toString should include("java-driver-core-shaded")
        case providers =>
          fail(s"expected exactly one CqlSession provider, found: ${providers.mkString(", ")}")
      }
    }

    "run a Helenus statement through a connector-yielded session without LinkageError" in {
      val conf = new SparkConf()
        .set("spark.cassandra.connection.host", "localhost")
        .set("spark.cassandra.connection.port", "9142")
        .set("spark.cassandra.connection.localDC", "datacenter1")

      val version = CassandraConnector(conf).withSessionDo { implicit session =>
        val pstmt = "SELECT release_version FROM system.local".toCQL(session).prepareUnit
        session.execute(pstmt.tupled(())).one().getString("release_version")
      }

      version should not be empty
    }
  }
}
