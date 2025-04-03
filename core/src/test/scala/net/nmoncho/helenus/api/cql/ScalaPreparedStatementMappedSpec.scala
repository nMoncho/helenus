/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import scala.concurrent.ExecutionContext.Implicits.global

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.OptionValues._
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class ScalaPreparedStatementMappedSpec
    extends AnyWordSpec
    with Matchers
    with Eventually
    with CassandraSpec
    with ScalaFutures {

  import HotelsTestData._

  private implicit lazy val cqlSession: CqlSession = session

  private implicit val adapter: Mapping[Hotel] = Mapping[Hotel]()

  "ScalaPreparedStatementFrom" should {
    "prepare a query" in {
      val insert =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepareFrom[Hotel]

      insert.execute(Hotels.h1)
      insert.execute(Hotels.h1.copy(name = null))
      insert.execute(Hotels.h1.copy(pois = null))

      val query = """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL
        .prepare[String]
        .as[Hotel]

      query.execute(Hotels.h1.id).nextOption() shouldBe Some(Hotels.h1)

      val mappedQuery = """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL
        .prepareFrom[Hotel]
        .as[Hotel]

      mappedQuery.execute(Hotels.h1).nextOption() shouldBe Some(Hotels.h1)

      withClue("and handle not ignore null fields") {
        val insertWithNulls = insert.withIgnoreNullFields(ignore = false)

        insertWithNulls.execute(Hotels.h1)
        insertWithNulls.execute(Hotels.h1.copy(name = null))
        insertWithNulls.execute(Hotels.h1.copy(pois = null))
      }
    }

    "prepare a query (async)" in {
      val insert =
        """INSERT INTO hotels(id, name, phone, address, pois)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQLAsync
          .prepareFrom[Hotel]

      val mappedQuery =
        """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL.prepareFrom[Hotel].as[Hotel]

      val tx = for {
        _ <- insert.executeAsync(Hotels.h1)
        q <- mappedQuery.executeAsync(Hotels.h1)
        r <- q.nextOption()
      } yield r

      whenReady(tx) { result =>
        result shouldBe defined
        result.value._1 shouldBe Hotels.h1
      }
    }
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
  }

}
