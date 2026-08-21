/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Offset and position accuracy across edge cases.
  *
  * `bindMarkerOffsets` and `firstErrorOffset` drive the interpolator's bind-versus-inject decision,
  * so an off-by-one — on a multi-line statement, around comments, or with multi-byte characters — is
  * a correctness bug, not a cosmetic one. Every assertion here pins the '''absolute''' offset
  * exactly, expressed as an index into the original Scala `String` (`indexOf` / `lastIndexOf`), which
  * is the space the interpolator indexes.
  *
  * The multi-byte cases matter because ANTLR indexes tokens by Unicode code point while the `String`
  * is indexed by UTF-16 char; `CqlValidator` translates back to char space (see `toCharIndex`), and
  * these tests are the regression net for that translation — in particular for supplementary
  * characters (emoji), where one code point is two chars.
  */
class CqlOffsetSpec extends AnyFlatSpec with Matchers {

  behavior of "bindMarkerOffsets"

  it should "report the offset of a single named marker" in {
    val q = "SELECT * FROM t WHERE id = :id"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "report every named marker offset" in {
    val q = "SELECT * FROM t WHERE a = :a AND b = :b"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":a"), q.indexOf(":b"))
  }

  it should "report absolute offsets on a multi-line statement" in {
    val q = "SELECT *\nFROM t\nWHERE id = :id AND name = :name"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"), q.indexOf(":name"))
  }

  it should "report the offset of a marker after a block comment" in {
    val q = "SELECT * FROM t /* pick one */ WHERE id = :id"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "report the offset of a marker after a line comment" in {
    val q = "SELECT * FROM t -- comment\nWHERE id = :id"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "not treat ':name' inside a string literal as a marker" in {
    val q = "SELECT * FROM t WHERE s = ':id'"
    CqlValidator.bindMarkerOffsets(q) shouldBe empty
  }

  it should "not treat ':name' inside a block comment as a marker" in {
    val q = "SELECT * FROM t /* :id */ WHERE x = ?"
    CqlValidator.bindMarkerOffsets(q) shouldBe empty
  }

  it should "not treat a positional '?' as a named marker" in {
    CqlValidator.bindMarkerOffsets("SELECT * FROM t WHERE id = ? AND n = ?") shouldBe empty
  }

  it should "keep the offset exact after a BMP multi-byte string literal" in {
    val q = "SELECT * FROM t WHERE label = 'café' AND id = :id"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "keep the offset exact after a supplementary character (emoji)" in {
    val q = "SELECT * FROM t WHERE note = '😀' AND id = :id"
    // The emoji is one code point but two UTF-16 chars: the offset must be the char index.
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":id"))
  }

  it should "keep offsets exact after several supplementary characters" in {
    val q = "SELECT * FROM t WHERE note = '😀🎉😀' AND a = :a AND b = :b"
    CqlValidator.bindMarkerOffsets(q) shouldBe Set(q.indexOf(":a"), q.indexOf(":b"))
  }

  behavior of "firstErrorOffset"

  it should "be None for a valid statement" in {
    CqlValidator.firstErrorOffset("SELECT * FROM t WHERE id = ?") shouldBe None
  }

  it should "point at the offending token" in {
    val q = "SELECT * FROM t WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "report the absolute (not in-line) offset on a multi-line statement" in {
    val q = "SELECT *\nFROM t WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "report the absolute offset across several blank lines" in {
    val q = "SELECT *\n\n\nFROM t WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "report the absolute offset after a block comment" in {
    val q = "SELECT * FROM t /* comment */ WHERE id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "keep the error offset exact after a BMP multi-byte string" in {
    val q = "SELECT * FROM t WHERE label = 'café' AND id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }

  it should "keep the error offset exact after a supplementary character (emoji)" in {
    val q = "SELECT * FROM t WHERE note = '😀' AND id = foo"
    CqlValidator.firstErrorOffset(q) shouldBe Some(q.indexOf("foo"))
  }
}
