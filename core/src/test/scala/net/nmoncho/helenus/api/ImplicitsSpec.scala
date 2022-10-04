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

package net.nmoncho.helenus.api

import net.nmoncho.helenus.CassandraSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class ImplicitsSpec extends AnyWordSpec with Matchers with CassandraSpec with ScalaFutures {

  import net.nmoncho.helenus._
  implicit lazy val s: CqlSessionExtension             = session.toScala
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "Implicits" should {
    "prepare a sync statement" in {
      withClue("with one parameter") {
        val uuid = UUID.randomUUID()

        val query = "SELECT * FROM implicits_tests WHERE id = ?".toCQL
          .prepare[UUID]

        query(uuid).execute().one() shouldBe null
      }

      withClue("with two parameter") {
        val uuid = UUID.randomUUID()
        val query = "SELECT * FROM implicits_tests WHERE id = ? AND age = ?".toCQL
          .prepare[UUID, Int]

        query(uuid, 2).execute().one() shouldBe null
      }
    }

    "prepare an async statement" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      withClue("with one parameter") {
        val uuid = UUID.randomUUID()

        val query = "SELECT * FROM implicits_tests WHERE id = ?".toCQL
          .prepareAsync[UUID]

        whenReady(query.map(_.apply(uuid).execute().one())) { row =>
          row shouldBe null
        }
      }

      withClue("with two parameter") {
        val uuid = UUID.randomUUID()
        val query = "SELECT * FROM implicits_tests WHERE id = ? AND age = ?".toCQL
          .prepareAsync[UUID, Int]

        whenReady(query.map(_.apply(uuid, 2).execute().one())) { row =>
          row shouldBe null
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    session.execute("""CREATE TABLE IF NOT EXISTS implicits_tests(
                      |   id     UUID,
                      |   age    INT,
                      |   name   TEXT,
                      |   PRIMARY KEY (id, age)
                      |)""".stripMargin)
  }
}
