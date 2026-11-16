/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import net.nmoncho.helenus.Scaladoc
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Verifies the CQL macros expand under Scaladoc, see [[Scaladoc]]. */
class CqlQueryInterpolationScaladocSpec extends AnyFlatSpec with Matchers {

  private val source =
    """import net.nmoncho.helenus._
      |import com.datastax.oss.driver.api.core.CqlSession
      |
      |object Metrics {
      |  final val tableName = "metrics"
      |  final val ownerId   = "owner_id"
      |  final val count     = "count"
      |}
      |
      |object Repository {
      |  import Metrics._
      |
      |  implicit val session: CqlSession = null
      |  implicit val ec: scala.concurrent.ExecutionContext =
      |    scala.concurrent.ExecutionContext.global
      |
      |  def toCql = s"UPDATE $tableName SET $count = $count + ? WHERE $ownerId = ?".toCQL
      |
      |  def toCqlAsync = s"UPDATE $tableName SET $count = $count + ? WHERE $ownerId = ?".toCQLAsync
      |
      |  def interpolated(id: String) = cql"SELECT * FROM $tableName WHERE $ownerId = $id"
      |
      |  def interpolatedAsync(id: String) = cqlAsync"SELECT * FROM $tableName WHERE $ownerId = $id"
      |}
      |""".stripMargin

  "CQL macros" should "expand over `final val`s under Scaladoc" in {
    Scaladoc.errorsOf(source) shouldBe empty
  }
}
