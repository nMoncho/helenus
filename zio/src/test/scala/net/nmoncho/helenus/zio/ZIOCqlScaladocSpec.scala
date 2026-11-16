/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

import _root_.zio.test._
import net.nmoncho.helenus.Scaladoc

/** Verifies the ZIO CQL macros expand under Scaladoc, see [[Scaladoc]]. */
object ZIOCqlScaladocSpec extends ZIOSpecDefault {

  private val source =
    """import net.nmoncho.helenus.zio._
      |
      |object Metrics {
      |  final val tableName = "rt_metrics"
      |  final val ownerId   = "owner_id"
      |}
      |
      |object Repository {
      |  import Metrics._
      |
      |  def interpolated(id: String) = zcql"SELECT * FROM $tableName WHERE $ownerId = $id"
      |
      |  def interpolatedAsync(id: String) = zcqlAsync"SELECT * FROM $tableName WHERE $ownerId = $id"
      |}
      |""".stripMargin

  override def spec: Spec[Any, Nothing] =
    test("ZIO CQL macros expand over `final val`s under Scaladoc") {
      assertTrue(Scaladoc.errorsOf(source).isEmpty)
    }
}
