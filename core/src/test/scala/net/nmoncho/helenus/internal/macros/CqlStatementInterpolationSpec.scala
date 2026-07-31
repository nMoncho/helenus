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

/** Verifies which CQL statement [[CqlQueryInterpolation.cql]] builds out of an interpolation.
  *
  * An interpolated parameter is either bound, or injected into the query text as is, and which
  * one applies is decided by the position it sits at: wherever CQL takes a value it becomes a
  * bind marker, even when it's a compile-time constant, and everywhere else - a table name, a
  * column name - it's injected, which only a constant can do. This lets both be mixed in a
  * single interpolation.
  *
  * The statement is read back from the expanded tree rather than from a `BoundStatement`, which
  * keeps these cases independent from a database.
  */
class CqlStatementInterpolationSpec extends AnyFlatSpec with Matchers {

  private lazy val tb: ToolBox[universe.type] = {
    val cl   = getClass.getClassLoader
    val urls = Iterator
      .iterate(cl)(_.getParent)
      .takeWhile(_ != null)
      .collect { case u: java.net.URLClassLoader => u.getURLs }
      .flatten
      .map(_.getFile)
      .mkString(java.io.File.pathSeparator)

    // The class loader only exposes the classpath as URLs when the suite runs under a nested
    // loader, as sbt does; otherwise it has to be read back from the JVM
    val cp = if (urls.isEmpty) System.getProperty("java.class.path") else urls

    universe.runtimeMirror(cl).mkToolBox(options = s"-cp $cp")
  }

  // `session` only needs to type check: these cases stop at macro expansion, they never prepare
  // anything
  private val preamble =
    """import net.nmoncho.helenus._
      |import com.datastax.oss.driver.api.core.CqlSession
      |
      |implicit val session: CqlSession = null.asInstanceOf[CqlSession]
      |
      |object K {
      |  final val tableName = "users"
      |  final val id        = "id"
      |  final val name      = "name"
      |  final val age       = "age"
      |
      |  final val DefaultName = "helenus"
      |  final val DefaultAge  = 42
      |}
      |import K._
      |""".stripMargin

  /** The statement the macro settled on is the one it prepares, so it can be picked up from the
    * expanded tree
    */
  private def statementOf(snippet: String): String = {
    import universe._

    val prepared = tb.typecheck(tb.parse(preamble + snippet)).collect {
      case Apply(Select(_, TermName("prepare")), List(Literal(Constant(stmt: String)))) => stmt
    }

    prepared match {
      case stmt :: Nil => stmt
      case other => fail(s"Expected exactly one prepared statement, got $other")
    }
  }

  /** Every value the expanded tree binds, by bind marker name */
  private def boundOf(snippet: String): Map[String, String] = {
    import universe._

    // `set` is generic, so the typed tree applies the type argument before the value arguments
    tb.typecheck(tb.parse(preamble + snippet))
      .collect {
        case Apply(
              TypeApply(Select(_, TermName("set")), _),
              List(Literal(Constant(name: String)), value, _)
            ) =>
          name -> showCode(value)
      }
      .toMap
  }

  private def errorOf(snippet: String): String =
    intercept[ToolBoxError](tb.typecheck(tb.parse(preamble + snippet))).getMessage

  // ---------------------------------------------------------------------------
  // Injected positions: a bind marker isn't allowed, so the constant goes in as is
  // ---------------------------------------------------------------------------

  "cql" should "inject String constants used as table and column names" in {
    statementOf("""cql"SELECT $id, $name FROM $tableName"""") shouldBe
    "SELECT id, name FROM users"
  }

  it should "inject String constants used as the column names of an INSERT" in {
    statementOf("""cql"INSERT INTO $tableName($id, $name) VALUES (?, ?)"""") shouldBe
    "INSERT INTO users(id, name) VALUES (?, ?)"
  }

  it should "inject a constant used where CQL only takes a number" in {
    statementOf("""cql"SELECT * FROM $tableName LIMIT $DefaultAge"""") shouldBe
    "SELECT * FROM users LIMIT 42"
  }

  // ---------------------------------------------------------------------------
  // Bound positions: a bind marker is allowed, so the constant is bound
  // ---------------------------------------------------------------------------

  it should "bind a String constant used as a value" in {
    statementOf(
      """cql"SELECT * FROM $tableName WHERE $name = $DefaultName ALLOW FILTERING""""
    ) shouldBe
    "SELECT * FROM users WHERE name = :p2 ALLOW FILTERING"
  }

  it should "bind the constant itself, through its own TypeCodec" in {
    boundOf(
      """cql"SELECT * FROM $tableName WHERE $name = $DefaultName ALLOW FILTERING""""
    ) shouldBe Map("p2" -> "\"helenus\"")
  }

  it should "bind constants used as the values of an INSERT" in {
    statementOf(
      """cql"INSERT INTO $tableName($name, $age) VALUES ($DefaultName, $DefaultAge)""""
    ) shouldBe "INSERT INTO users(name, age) VALUES (:p3, :p4)"
  }

  it should "bind a String constant used as the value of an UPDATE" in {
    statementOf("""cql"UPDATE $tableName SET $name = $DefaultName WHERE $id = ?"""") shouldBe
    "UPDATE users SET name = :p2 WHERE id = ?"
  }

  it should "decide per position, even for the very same constant" in {
    statementOf("""cql"SELECT * FROM $name WHERE $name = $name ALLOW FILTERING"""") shouldBe
    "SELECT * FROM name WHERE name = :p2 ALLOW FILTERING"
  }

  it should "decide per position on a multi-line statement" in {
    // A real newline, so that the offending token's position within its own line no longer
    // matches its position within the statement
    statementOf(
      "cql\"\"\"SELECT *\nFROM $tableName\nWHERE $name = $DefaultName\nALLOW FILTERING\"\"\""
    ) shouldBe "SELECT *\nFROM users\nWHERE name = :p2\nALLOW FILTERING"
  }

  it should "keep the name of parameters that have one" in {
    statementOf(
      """
        |val nickname = "helenus"
        |cql"SELECT * FROM $tableName WHERE $name = $nickname ALLOW FILTERING"
        |""".stripMargin
    ) shouldBe "SELECT * FROM users WHERE name = :nickname ALLOW FILTERING"
  }

  it should "not reuse the marker of a parameter that came with its own name" in {
    // `$DefaultName` is the third parameter, so its marker would be derived as `p2` and collide
    // with the name the fifth one already has, binding both of them to the same marker
    statementOf(
      """
        |val p2 = "helenus"
        |cql"SELECT * FROM $tableName WHERE $name = $DefaultName AND $age = $p2 ALLOW FILTERING"
        |""".stripMargin
    ) shouldBe "SELECT * FROM users WHERE name = :p2_ AND age = :p2 ALLOW FILTERING"
  }

  // ---------------------------------------------------------------------------
  // Backwards compatibility
  // ---------------------------------------------------------------------------

  it should "inject a String constant the caller already quoted" in {
    // `':p2'` would parse, but as part of a string literal rather than as a bind marker, so
    // there would be nothing to bind the value to
    statementOf(
      """cql"SELECT * FROM $tableName WHERE $name = '$DefaultName' ALLOW FILTERING""""
    ) shouldBe "SELECT * FROM users WHERE name = 'helenus' ALLOW FILTERING"
  }

  it should "bind nothing for a statement whose constants are all injected" in {
    boundOf(
      """cql"SELECT * FROM $tableName WHERE $name = '$DefaultName' ALLOW FILTERING""""
    ) shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // Invalid statements still fail, reporting the statement that was built
  // ---------------------------------------------------------------------------

  it should "reject a statement that no arrangement can make valid" in {
    val msg = errorOf("""cql"SELEKT * FROM $tableName WHERE $name = $DefaultName"""")

    msg should include("Invalid CQL")
    msg should include("SELEKT * FROM users WHERE name = helenus")
  }

  it should "reject a value that isn't a compile-time constant where CQL wants an identifier" in {
    val msg = errorOf(
      """
        |val tbl = "users"
        |cql"SELECT * FROM $tbl"
        |""".stripMargin
    )

    msg should include("Invalid CQL")
    msg should include("SELECT * FROM :tbl")
  }
}
