/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.reflect.runtime.universe
import scala.tools.reflect.ToolBox
import scala.tools.reflect.ToolBoxError

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Verifies the diagnostics produced by [[CqlQueryInterpolation.toCQL]] and
  * [[CqlQueryInterpolation.toCQLAsync]] when the interpolated string isn't a compile-time
  * constant, so that a mistake (e.g. a `val` whose explicit type annotation widened away its
  * literal type) surfaces as a specific, actionable message instead of a generic one, or
  * (worse) a confusing downstream CQL parse error.
  */
class CqlQueryInterpolationDiagnosticsSpec extends AnyFlatSpec with Matchers {

  private lazy val tb: ToolBox[universe.type] = {
    val cl = getClass.getClassLoader
    val cp = Iterator
      .iterate(cl)(_.getParent)
      .takeWhile(_ != null)
      .collect { case u: java.net.URLClassLoader => u.getURLs }
      .flatten
      .map(_.getFile)
      .mkString(java.io.File.pathSeparator)
    universe.runtimeMirror(cl).mkToolBox(options = s"-cp $cp")
  }

  // `session` only needs to type check: `CQLQuery` is a plain case class that never
  // dereferences it, and macro expansion aborts before that point in the error cases.
  // `net.nmoncho.helenus` already provides an implicit `CqlSession => Future[CqlSession]`
  // adapter, so `toCQLAsync`'s `Future[CqlSession]` implicit is resolved from `session` alone.
  private val preamble =
    """import net.nmoncho.helenus._
      |import net.nmoncho.helenus.internal.macros.CqlQueryInterpolationDiagnosticsFixtures._
      |import com.datastax.oss.driver.api.core.CqlSession
      |
      |implicit val session: CqlSession = null.asInstanceOf[CqlSession]
      |implicit val ec: scala.concurrent.ExecutionContext =
      |  scala.concurrent.ExecutionContext.global
      |""".stripMargin

  private def errorOf(snippet: String): String = {
    val ex = intercept[ToolBoxError](tb.typecheck(tb.parse(preamble + snippet)))
    ex.getMessage
  }

  private def queryOf(snippet: String): String =
    tb.eval(tb.parse(preamble + snippet)).asInstanceOf[String]

  private def shouldCompile(snippet: String): Unit =
    noException should be thrownBy tb.typecheck(tb.parse(preamble + snippet))

  // ---------------------------------------------------------------------------
  // toCQL: regression coverage for the underlying `+`-chain fix
  // ---------------------------------------------------------------------------

  "toCQL" should "fold an interpolation over plain (unannotated) `final val`s" in {
    val query = queryOf(
      """
        |object K {
        |  final val tableName = "the_table"
        |  final val pvin = "p_vin"
        |}
        |import K._
        |s"SELECT * FROM $tableName WHERE $pvin = ?".toCQL.query
        |""".stripMargin
    )

    query shouldBe "SELECT * FROM the_table WHERE p_vin = ?"
  }

  it should "fold an interpolation with more than two interpolated parts (regression: used to keep only the last one)" in {
    val query = queryOf(
      """
        |object K {
        |  final val tableName = "the_table"
        |  final val pvin = "p_vin"
        |  final val creationTs = "creation_time"
        |}
        |import K._
        |s"SELECT * FROM $tableName WHERE $pvin = ? AND $creationTs >= ? AND $creationTs <= ?".toCQL.query
        |""".stripMargin
    )

    query shouldBe "SELECT * FROM the_table WHERE p_vin = ? AND creation_time >= ? AND creation_time <= ?"
  }

  // ---------------------------------------------------------------------------
  // toCQL: diagnostics
  // ---------------------------------------------------------------------------

  it should "explain that an explicitly-typed `final val` isn't a compile-time constant" in {
    val msg = errorOf(
      """s"SELECT * FROM $widenedTableName".toCQL"""
    )

    msg should include("toCQL requires a compile-time constant string")
    msg should include("widenedTableName")
    msg should include("compile-time constant")
    msg should include("explicit type annotation")
    msg should include("final val widenedTableName")
  }

  it should "point at the specific offending value in a chain of otherwise-foldable parts" in {
    val msg = errorOf(
      """s"SELECT * FROM $widenedTableName WHERE $plainPvin = ?".toCQL"""
    )

    msg should include("widenedTableName")
  }

  it should "explain that a `var` can never be a compile-time constant" in {
    val msg = errorOf(
      """s"SELECT * FROM $mutableThing".toCQL"""
    )

    msg should include("mutableThing")
    msg should include("var")
    msg should include("runtime")
  }

  it should "explain that a local value (e.g. a method parameter) can never be a compile-time constant" in {
    val msg = errorOf(
      """
        |val x = "the_table"
        |s"SELECT * FROM $x".toCQL
        |""".stripMargin
    )

    msg should include("x")
    msg should include("runtime value")
  }

  it should "reject a fully runtime-computed value with a generic, still-clear diagnostic" in {
    val msg = errorOf(
      """
        |s"SELECT * FROM ${List(1, 2).mkString}".toCQL
        |""".stripMargin
    )

    msg should include("toCQL requires a compile-time constant string")
    msg should include("mkString")
    msg should include("can't be a compile-time constant")
  }

  // ---------------------------------------------------------------------------
  // toCQLAsync: same diagnostics apply
  // ---------------------------------------------------------------------------

  "toCQLAsync" should "fold an interpolation over plain (unannotated) `final val`s" in {
    val query = queryOf(
      """
        |object K {
        |  final val tableName = "the_table"
        |  final val pvin = "p_vin"
        |  final val creationTs = "creation_time"
        |}
        |import K._
        |scala.concurrent.Await.result(
        |  s"SELECT * FROM $tableName WHERE $pvin = ? AND $creationTs >= ? AND $creationTs <= ?".toCQLAsync,
        |  scala.concurrent.duration.Duration.Inf
        |).query
        |""".stripMargin
    )

    query shouldBe "SELECT * FROM the_table WHERE p_vin = ? AND creation_time >= ? AND creation_time <= ?"
  }

  it should "explain that an explicitly-typed `final val` isn't a compile-time constant" in {
    val msg = errorOf(
      """s"SELECT * FROM $widenedTableName".toCQLAsync"""
    )

    msg should include("toCQLAsync requires a compile-time constant string")
    msg should include("widenedTableName")
    msg should include("explicit type annotation")
  }

  // ---------------------------------------------------------------------------
  // Sanity: still rejects non-string-literal usages the same way as before
  // ---------------------------------------------------------------------------

  it should "still compile a plain string literal" in
    shouldCompile("""s"SELECT * FROM users".toCQL""")
}
