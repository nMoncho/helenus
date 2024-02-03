/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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

      val query =
        """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL.prepare[String].as[Hotel]

      query.execute(Hotels.h1.id).nextOption() shouldBe Some(Hotels.h1)

      val mappedQuery =
        """SELECT * FROM hotels WHERE id = ?""".stripMargin.toCQL.prepareFrom[Hotel].as[Hotel]

      mappedQuery.execute(Hotels.h1).nextOption() shouldBe Some(Hotels.h1)
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
