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

import java.util.UUID

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class CqlQueryInterpolationSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with ScalaFutures {

  private implicit lazy val cqlSession: CqlSession = session

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "CQL Query interpolation" should {
    val id   = UUID.fromString("81a410a2-dc22-4ef9-85d7-6a7a0f64232f")
    val age  = 42
    val name = "helenus"

    "run synchronously" in {
      // FIXME There should be a way to interpolate the table name without messing with the parameters

      withClue("return on empty table") {
        val query = cql"SELECT * FROM cql_interpolation_test WHERE id = $id"
        query.execute().one() shouldBe null
      }

      withClue("return on empty table") {
        val insert =
          cql"INSERT INTO cql_interpolation_test(id, age, name) VALUES ($id, $age, $name)"
        val query = cql"SELECT * FROM cql_interpolation_test WHERE id = ${id}"

        insert.execute()
        val row = Option(query.execute().one())

        row shouldBe defined
        row.foreach(_.getUuid(0) shouldBe id)
      }
    }

    "run asynchronously" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      withClue("return on empty table") {
        val query = asyncCql"SELECT * FROM cql_interpolation_test WHERE id = ${id}"

        whenReady(query.map(_.execute().one()))(_ shouldBe null)
      }

      withClue("return on empty table") {
        val insert =
          asyncCql"INSERT INTO cql_interpolation_test(id, age, name) VALUES ($id, $age, $name)"
        val query = asyncCql"SELECT * FROM cql_interpolation_test WHERE id = ${id}"

        val tx = for {
          insertBstmt <- insert
          _ <- insertBstmt.executeAsync()
          queryBstmt <- query
          row <- queryBstmt.executeAsync()
        } yield Option(row.one())

        whenReady(tx) { row =>
          row shouldBe defined
          row.foreach(_.getUuid(0) shouldBe id)
        }

      }
    }

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("""CREATE TABLE IF NOT EXISTS cql_interpolation_test(
        |   id     UUID,
        |   age    INT,
        |   name   TEXT,
        |   PRIMARY KEY (id, age)
        |)""".stripMargin)
  }
}
