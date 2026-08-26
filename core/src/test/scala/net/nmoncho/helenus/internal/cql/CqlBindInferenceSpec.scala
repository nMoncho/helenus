/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

/** Property tests for the bind-versus-inject settle loop.
  *
  * [[BindInference.boundParams]] decides, per interpolated parameter, whether it is a '''value''' to
  * bind (a `:name` marker) or a fragment to '''inject''' into the query text (a table or column
  * name). It is subtle, it iterates, flipping parameters from bound to injected until the statement
  * parses, and was previously only exercised indirectly through end-to-end interpolation. Here it is
  * driven directly, over generated interpolations that mix table names, column names and values.
  *
  * An interpolation is modelled as a list of [[CqlBindInferenceSpec.Frag]]s: literal text, an
  * '''identifier''' slot (a compile-time constant, so injectable, expected to be injected), or a
  * '''value''' slot (a runtime value or a constant in value position, expected to be bound). The
  * model records the expected decision for each slot, which the property checks against, and then
  * asserts the emitted statement is valid CQL.
  */
class CqlBindInferenceSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  import CqlBindInferenceSpec._

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 200)

  private def valid(stmt: String): Unit =
    withClue(s"emitted statement must be valid CQL: [$stmt]\n  ")(
      CqlValidator.validate(stmt) shouldBe Right(())
    )

  behavior of "boundParams (the bind-versus-inject settle loop)"

  it should "bind values, inject identifiers, and emit valid CQL" in
    forAll(genStatement) { frags =>
      val r     = render(frags)
      val bound = BindInference.boundParams(r.parts, r.names, r.injectable)

      withClue(s"statement=[${r.rendered(bound)}] frags=$frags\n  ") {
        bound shouldBe r.expectedBound
      }
      valid(r.rendered(bound))
    }

  it should "keep a constant value bound in value position (not injected)" in
    forAll(safeLiteral) { lit =>
      val parts      = Seq("SELECT * FROM t WHERE id = ", "")
      val names      = Seq("v0")
      val injectable = Seq(Some(lit))

      // A marker is valid in a value position, so even a constant stays bound.
      BindInference.boundParams(parts, names, injectable) shouldBe Seq(true)
    }

  it should "inject a constant that was swallowed into a string literal" in
    forAll(Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)) { txt =>
      // The interpolation wrapped the parameter in quotes, so the initial `:v0` lands inside a
      // string literal (a valid statement) rather than as a marker of its own. The settle loop must
      // notice it was swallowed and inject the constant instead.
      val parts      = Seq("SELECT * FROM t WHERE s = '", "'")
      val names      = Seq("v0")
      val injectable = Seq(Some(txt))

      val bound = BindInference.boundParams(parts, names, injectable)
      bound shouldBe Seq(false)

      val stmt = BindInference.interleave(parts, BindInference.tokensOf(names, injectable, bound))
      stmt shouldBe s"SELECT * FROM t WHERE s = '$txt'"
      valid(stmt)
    }

  behavior of "boundParams when the statement cannot be made valid"

  it should "leave a non-injectable identifier bound and the statement invalid" in {
    // `cql"SELECT * FROM $table"` with a runtime `table`: it belongs in an identifier position but
    // cannot be injected, so it stays bound and the caller's validation reports the error.
    val parts      = Seq("SELECT * FROM ", "")
    val names      = Seq("tbl")
    val injectable = Seq(Option.empty[String])

    val bound = BindInference.boundParams(parts, names, injectable)
    bound shouldBe Seq(true)

    val stmt = BindInference.interleave(parts, BindInference.tokensOf(names, injectable, bound))
    stmt shouldBe "SELECT * FROM :tbl"
    CqlValidator.validate(stmt) should matchPattern { case Left(_) => }
  }

  it should "inject every constant but bind the non-injectable ones when the statement is invalid" in {
    // A constant column selector (injectable) and a runtime table (not): the fallback injects the
    // constant as written and binds the rest, so the reported error points at the user's text.
    val parts      = Seq("SELECT ", " FROM ", "")
    val names      = Seq("col", "tbl")
    val injectable = Seq(Some("name"), None)

    val bound = BindInference.boundParams(parts, names, injectable)
    bound shouldBe Seq(false, true)

    val stmt = BindInference.interleave(parts, BindInference.tokensOf(names, injectable, bound))
    stmt shouldBe "SELECT name FROM :tbl"
    CqlValidator.validate(stmt) should matchPattern { case Left(_) => }
  }

  it should "be deterministic (idempotent) for the same input" in
    forAll(genStatement) { frags =>
      val r = render(frags)
      BindInference.boundParams(r.parts, r.names, r.injectable) shouldBe
      BindInference.boundParams(r.parts, r.names, r.injectable)
    }
}

object CqlBindInferenceSpec {

  /** A fragment of a modelled interpolation. */
  sealed trait Frag
  final case class Lit(text: String) extends Frag // constant text
  final case class IdSlot(text: String) extends Frag // interpolated identifier (injectable)
  final case class ValSlot(injectable: Option[String]) extends Frag // interpolated value (bound)

  /** The flattened `(parts, names, injectable)` a `boundParams` call takes, plus the decision the
    * model expects and a way to render the resulting statement.
    */
  final case class Rendered(
      parts: Seq[String],
      names: Seq[String],
      injectable: Seq[Option[String]],
      expectedBound: Seq[Boolean]
  ) {
    def rendered(bound: Seq[Boolean]): String =
      BindInference.interleave(parts, BindInference.tokensOf(names, injectable, bound))
  }

  def render(frags: Seq[Frag]): Rendered = {
    val parts      = scala.collection.mutable.ArrayBuffer("")
    val names      = scala.collection.mutable.ArrayBuffer.empty[String]
    val injectable = scala.collection.mutable.ArrayBuffer.empty[Option[String]]
    val expected   = scala.collection.mutable.ArrayBuffer.empty[Boolean]

    def slot(name: String, inj: Option[String], bound: Boolean): Unit = {
      names += name; injectable += inj; expected += bound; parts += ""
    }

    frags.foreach {
      case Lit(t) => parts(parts.size - 1) = parts.last + t
      case IdSlot(t) => slot(s"c${names.size}", Some(t), bound = false) // identifier → injected
      case ValSlot(oi) => slot(s"v${names.size}", oi, bound = true) // value → bound
    }

    Rendered(parts.toSeq, names.toSeq, injectable.toSeq, expected.toSeq)
  }

  private val safeIdent: Gen[String] =
    Gen.oneOf(
      "id",
      "name",
      "email",
      "a",
      "b",
      "c",
      "x",
      "y",
      "data",
      "ts",
      "n",
      "m",
      "l",
      "s",
      "tags",
      "addr",
      "score",
      "amount",
      "status",
      "code",
      "col",
      "tbl",
      "pk"
    )

  private val safeLiteral: Gen[String] =
    Gen.oneOf(
      Gen.choose(0, 100000).map(_.toString),
      Gen.nonEmptyListOf(Gen.alphaNumChar).map(cs => "'" + cs.take(10).mkString + "'"),
      Gen.oneOf("true", "false")
    )

  // An identifier, sometimes written literally and sometimes interpolated (so mixed literal and
  // interpolated identifiers are exercised). Both render to the same text.
  private val idFrag: Gen[Seq[Frag]] =
    safeIdent.flatMap(n => Gen.oneOf[Seq[Frag]](Seq(Lit(n)), Seq(IdSlot(n))))

  private val valSlot: Gen[Frag] =
    Gen.oneOf(Gen.const(ValSlot(None): Frag), safeLiteral.map(l => ValSlot(Some(l)): Frag))

  private def joinFrags(groups: Seq[Seq[Frag]], sep: String): Seq[Frag] =
    groups.zipWithIndex.flatMap { case (g, i) => if (i == 0) g else Lit(sep) +: g }

  private val selectors: Gen[Seq[Frag]] =
    Gen.oneOf(
      Gen.const(Seq[Frag](Lit("*"))),
      Gen.choose(1, 3).flatMap(k => Gen.listOfN(k, idFrag)).map(joinFrags(_, ", "))
    )

  private val relation: Gen[Seq[Frag]] =
    for { col <- idFrag; v <- valSlot } yield col ++ Seq(Lit(" = "), v)

  private val whereClause: Gen[Seq[Frag]] =
    Gen
      .choose(1, 3)
      .flatMap(k => Gen.listOfN(k, relation))
      .map(rs => Seq[Frag](Lit(" WHERE ")) ++ joinFrags(rs, " AND "))

  private val select: Gen[Seq[Frag]] =
    for {
      sel <- selectors
      tbl <- idFrag
      where <- Gen.oneOf(Gen.const(Seq.empty[Frag]), whereClause)
    } yield Seq[Frag](Lit("SELECT ")) ++ sel ++ Seq(Lit(" FROM ")) ++ tbl ++ where

  private val insert: Gen[Seq[Frag]] =
    for {
      tbl <- idFrag
      k <- Gen.choose(1, 4)
      cols <- Gen.listOfN(k, idFrag)
      vals <- Gen.listOfN(k, valSlot)
    } yield Seq[Frag](Lit("INSERT INTO ")) ++ tbl ++ Seq(Lit(" (")) ++ joinFrags(cols, ", ") ++
      Seq(Lit(") VALUES (")) ++ joinFrags(vals.map(Seq(_)), ", ") ++ Seq(Lit(")"))

  private val update: Gen[Seq[Frag]] =
    for {
      tbl <- idFrag
      k <- Gen.choose(1, 3)
      sets <- Gen.listOfN(k, for { c <- idFrag; v <- valSlot } yield c ++ Seq(Lit(" = "), v))
      where <- whereClause
    } yield Seq[Frag](Lit("UPDATE ")) ++ tbl ++ Seq(Lit(" SET ")) ++ joinFrags(sets, ", ") ++ where

  private val delete: Gen[Seq[Frag]] =
    for { tbl <- idFrag; where <- whereClause } yield Seq[Frag](Lit("DELETE FROM ")) ++ tbl ++ where

  val genStatement: Gen[Seq[Frag]] = Gen.oneOf(select, insert, update, delete)
}
