/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import java.util.UUID

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.OptionValues._
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

  import Keyspace._

  private implicit lazy val cqlSession: CqlSession = session

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "CQL Query interpolation" should {
    val id   = UUID.fromString("81a410a2-dc22-4ef9-85d7-6a7a0f64232f")
    val age  = 42
    val name = "helenus"

    "run synchronously" in {
      withClue("return on empty table") {
        val query =
          cql"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"

        query.execute().nextOption() should not be defined
      }

      withClue("return on empty table") {
        val insert =
          cql"INSERT INTO ${InterpolationTest.tableName}(${InterpolationTest.id}, ${InterpolationTest.age}, ${InterpolationTest.name}) VALUES ($id, $age, $name)"
        val query =
          cql"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"

        insert.execute()
        val row = query.execute().nextOption()

        row shouldBe defined
        row.foreach(_.getUuid(0) shouldBe id)
      }

      withClue("and adapt results with a RowMapper") {
        val query =
          cql"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"
            .as[(UUID, Int, String)]

        val row = query.execute().nextOption()

        row shouldBe defined
        row.foreach { case (rowId, _, _) => rowId shouldBe id }
      }

      withClue("and create a pager") {
        val query =
          cql"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"
            .as[(UUID, Int, String)]

        val pager = query.pager

        val (nextPager, page0) = pager.execute(2)

        page0.foreach { case (rowId, _, _) => rowId shouldBe id }
        nextPager.hasMorePages shouldBe false
      }
    }

    "run asynchronously" in {
      import scala.concurrent.ExecutionContext.Implicits.global

      withClue("return on empty table") {
        val query =
          cqlAsync"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"

        whenReady(query.map(_.execute().nextOption()))(_ should not be defined)
      }

      withClue("return on non-empty table") {
        val insert =
          cqlAsync"INSERT INTO ${InterpolationTest.tableName}(${InterpolationTest.id}, ${InterpolationTest.age}, ${InterpolationTest.name}) VALUES ($id, $age, $name)"
        val query =
          cqlAsync"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"

        val tx = for {
          insertBstmt <- insert
          _ <- insertBstmt.executeAsync()
          queryBstmt <- query
          row <- queryBstmt.executeAsync()
        } yield Option(row.one())

        whenReady(tx) { row =>
          row shouldBe defined
          row.value.getUuid(0) shouldBe id
        }
      }

      withClue("return on non-empty table, with short-hand methods") {
        val insert =
          cqlAsync"INSERT INTO ${InterpolationTest.tableName}(${InterpolationTest.id}, ${InterpolationTest.age}, ${InterpolationTest.name}) VALUES ($id, $age, $name)"
        val query =
          cqlAsync"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"
            .as[(UUID, Int, String)]

        val tx = for {
          _ <- insert.executeAsync()
          row <- query.executeAsync()
        } yield Option(row.one())

        whenReady(tx) { row =>
          row shouldBe defined
          row.foreach(_._1 shouldBe id)
        }
      }

      withClue("and create a pager") {
        val query =
          cqlAsync"SELECT * FROM ${InterpolationTest.tableName} WHERE ${InterpolationTest.id} = $id"
            .as[(UUID, Int, String)]

        val tx = for {
          pager <- query.pager
          (nextPager, page0) <- pager.executeAsync(2)
        } yield (nextPager, page0)

        whenReady(tx) { case (nextPager, page0) =>
          page0.foreach { case (rowId, _, _) => rowId shouldBe id }
          nextPager.hasMorePages shouldBe false
        }
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    import InterpolationTest._

    executeDDL(
      s"""CREATE TABLE IF NOT EXISTS $tableName(
      |   $id     UUID,
      |   $age    INT,
      |   $name   TEXT,
      |   PRIMARY KEY ($id, $age)
      |)""".stripMargin
    )
  }
}
