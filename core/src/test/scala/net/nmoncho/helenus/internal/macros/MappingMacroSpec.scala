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

// Tests that verify compile-time macro behaviour by driving a ToolBox compiler.
// Each tb.typecheck() call triggers a full macro expansion; c.abort() in the
// macro surfaces as a ToolBoxError whose message contains the compiler diagnostic.
class MappingMacroSpec extends AnyFlatSpec with Matchers {

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

  private val preamble =
    """import net.nmoncho.helenus._
      |import net.nmoncho.helenus.api.cql.Mapping
      |import net.nmoncho.helenus.models.Hotel
      |""".stripMargin

  private def shouldCompile(snippet: String): Unit =
    noException should be thrownBy tb.typecheck(tb.parse(preamble + snippet))

  private def errorOf(snippet: String): String = {
    val ex = intercept[ToolBoxError](tb.typecheck(tb.parse(preamble + snippet)))
    ex.getMessage
  }

  // ---------------------------------------------------------------------------
  // Mapping macro — valid derivations
  // ---------------------------------------------------------------------------

  "Mapping macro" should "derive a Mapping for a case class" in
    shouldCompile("""Mapping[Hotel]()""")

  it should "derive a Mapping renaming a field with a literal" in
    shouldCompile("""Mapping[Hotel](_.name -> "hotel_name")""")

  it should "derive a Mapping renaming several fields" in
    shouldCompile("""Mapping[Hotel](_.id -> "hotel_id", _.name -> "hotel_name")""")

  it should "derive a Mapping renaming a field with a constant" in
    shouldCompile(
      """object Columns { final val hotelName: String = "hotel_name" }
        |Mapping[Hotel](_.name -> Columns.hotelName)
        |""".stripMargin
    )

  it should "derive a Mapping renaming a field with an explicit function" in
    shouldCompile("""Mapping[Hotel]((hotel: Hotel) => hotel.name -> "hotel_name")""")

  // ---------------------------------------------------------------------------
  // Mapping macro — invalid derivations (compile-time errors)
  // ---------------------------------------------------------------------------

  it should "reject a tuple" in {
    val msg = errorOf("""Mapping[(String, String)]()""")
    msg should include("Only case classes are allowed to have a Mapping")
    msg should include("(String, String)")
  }

  it should "reject a type that isn't a case class" in {
    val msg = errorOf(
      """import net.nmoncho.helenus.internal.cql.DerivedMapping
        |class NotACaseClass(val id: String)
        |implicit val builder: DerivedMapping.Builder[NotACaseClass] =
        |  (_: Map[String, String]) => null
        |Mapping[NotACaseClass]()
        |""".stripMargin
    )
    msg should include("Only case classes are allowed to have a Mapping")
    msg should include("NotACaseClass")
  }

  it should "reject a renamed field that doesn't come from the case class" in {
    val msg = errorOf("""Mapping[Hotel](_ => "name" -> "hotel_name")""")
    msg should include("Cannot find fieldName")
  }
}
