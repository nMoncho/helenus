/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.internal.codec.CodecRoundTripPropertySpec.Person
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

/** G2: property-based round-trip coverage for the codecs, the correctness core
  * of the library. For every category the property is the same:
  * `decode(encode(x)) == x` across generated values, including the empty and
  * boundary inputs that example-based specs tend to miss. The null / empty
  * edge cases (where a codec intentionally normalizes, e.g. a null collection
  * decodes to empty, a `None` decodes to `None`) are pinned separately so the
  * documented normalization is regression-protected too.
  *
  * These run fully in memory (no live Cassandra); a codec is obtained the same
  * way user code obtains it, through `Codec[...]` / `Codec.of[...]`.
  */
class CodecRoundTripPropertySpec
    extends AnyWordSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 100)

  // Restrict generated strings to ASCII-printable so the UTF-8 TEXT codec
  // round-trips exactly: `arbitrary[String]` can emit lone surrogates, which
  // UTF-8 encoding replaces, breaking the round-trip through no fault of ours.
  // The empty string is still reachable (the generated list can be empty).
  implicit private val arbSafeString: Arbitrary[String] = Arbitrary(Gen.asciiPrintableStr)

  private def roundTrip[A](codec: TypeCodec[A], value: A): A =
    codec.decode(codec.encode(value, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

  "Primitive codecs" should {
    "round-trip Int" in {
      val codec = Codec[Int]
      forAll((v: Int) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip Long" in {
      val codec = Codec[Long]
      forAll((v: Long) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip Short" in {
      val codec = Codec[Short]
      forAll((v: Short) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip Byte" in {
      val codec = Codec[Byte]
      forAll((v: Byte) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip Boolean" in {
      val codec = Codec[Boolean]
      forAll((v: Boolean) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip Double (excluding NaN, which never equals itself)" in {
      val codec = Codec[Double]
      forAll(arbitrary[Double].map(d => if (d.isNaN) 0.0 else d)) { v =>
        roundTrip(codec, v) shouldBe v
      }
    }

    "round-trip Float (excluding NaN, which never equals itself)" in {
      val codec = Codec[Float]
      forAll(arbitrary[Float].map(f => if (f.isNaN) 0.0f else f)) { v =>
        roundTrip(codec, v) shouldBe v
      }
    }

    "round-trip String (including the empty string)" in {
      val codec = Codec[String]
      forAll((v: String) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip BigInt" in {
      val codec = Codec[BigInt]
      forAll((v: BigInt) => roundTrip(codec, v) shouldBe v)
    }

    "round-trip BigDecimal (value and scale)" in {
      val codec = Codec[BigDecimal]
      val gen   = for {
        unscaled <- arbitrary[Long]
        scale <- Gen.choose(-12, 12)
      } yield BigDecimal(BigInt(unscaled), scale)

      forAll(gen) { v =>
        val rt = roundTrip(codec, v)
        rt.compare(v) shouldBe 0 // same numeric value ...
        rt.scale shouldBe v.scale // ... and the DECIMAL scale is preserved
      }
    }

    "round-trip primitive boundary values" in {
      roundTrip(Codec[Int], Int.MinValue) shouldBe Int.MinValue
      roundTrip(Codec[Int], Int.MaxValue) shouldBe Int.MaxValue
      roundTrip(Codec[Long], Long.MinValue) shouldBe Long.MinValue
      roundTrip(Codec[Long], Long.MaxValue) shouldBe Long.MaxValue
      roundTrip(Codec[Short], Short.MinValue) shouldBe Short.MinValue
      roundTrip(Codec[Short], Short.MaxValue) shouldBe Short.MaxValue
      roundTrip(Codec[Byte], Byte.MinValue) shouldBe Byte.MinValue
      roundTrip(Codec[Byte], Byte.MaxValue) shouldBe Byte.MaxValue
      roundTrip(Codec[Double], Double.MinValue) shouldBe Double.MinValue
      roundTrip(Codec[Double], Double.MaxValue) shouldBe Double.MaxValue
      roundTrip(Codec[Double], Double.PositiveInfinity) shouldBe Double.PositiveInfinity
      roundTrip(Codec[Double], Double.NegativeInfinity) shouldBe Double.NegativeInfinity
      roundTrip(Codec[Float], Float.MinValue) shouldBe Float.MinValue
      roundTrip(Codec[Float], Float.MaxValue) shouldBe Float.MaxValue
    }
  }

  "Collection codecs" should {
    "round-trip List (including empty)" in {
      val codec = Codec[List[Int]]
      forAll((xs: List[Int]) => roundTrip(codec, xs) shouldBe xs)
    }

    "round-trip Vector (including empty)" in {
      val codec = Codec[Vector[Int]]
      forAll((xs: Vector[Int]) => roundTrip(codec, xs) shouldBe xs)
    }

    "round-trip Seq (including empty)" in {
      val codec = Codec[Seq[Int]]
      forAll { (xs: List[Int]) =>
        val seq: Seq[Int] = xs
        roundTrip(codec, seq) shouldBe seq
      }
    }

    "round-trip Set (including empty)" in {
      val codec = Codec[Set[Int]]
      forAll((xs: Set[Int]) => roundTrip(codec, xs) shouldBe xs)
    }

    "round-trip Map (including empty)" in {
      val codec = Codec[Map[String, Int]]
      forAll((m: Map[String, Int]) => roundTrip(codec, m) shouldBe m)
    }
  }

  "Tuple codecs" should {
    "round-trip a pair" in {
      val codec = Codec[(Int, String)]
      forAll((t: (Int, String)) => roundTrip(codec, t) shouldBe t)
    }

    "round-trip a triple" in {
      val codec = Codec[(Int, String, Boolean)]
      forAll((t: (Int, String, Boolean)) => roundTrip(codec, t) shouldBe t)
    }
  }

  "Option codecs" should {
    "round-trip Option[Int] (Some and None)" in {
      val codec = Codec[Option[Int]]
      forAll((o: Option[Int]) => roundTrip(codec, o) shouldBe o)
    }

    "round-trip Option[String] with non-empty values (Some and None)" in {
      val codec = Codec[Option[String]]
      // Non-empty strings only: see the empty-buffer collapse pinned below.
      val gen = Gen.option(Gen.asciiPrintableStr.map(s => if (s.isEmpty) "x" else s))
      forAll(gen)(o => roundTrip(codec, o) shouldBe o)
    }

    "collapse a value encoding to an empty buffer (Some(\"\")) to None" in {
      // An empty string encodes to a zero-length buffer, which is
      // indistinguishable from a CQL NULL, so OptionCodec.decode returns None.
      // This is intentional (empty buffer == absent); the property pins it so
      // the behaviour is not changed unknowingly.
      val codec = Codec[Option[String]]
      roundTrip(codec, Some("")) shouldBe None
    }
  }

  "Either codecs" should {
    "round-trip Either[Int, String] (Left and Right)" in {
      val codec = Codec[Either[Int, String]]
      forAll((e: Either[Int, String]) => roundTrip(codec, e) shouldBe e)
    }
  }

  "UDT codecs" should {
    "round-trip a case class with primitive, Option and tuple fields" in {
      val codec = Codec.of[Person]()
      forAll((p: Person) => roundTrip(codec, p) shouldBe p)
    }
  }

  "Null and empty inputs" should {
    "decode a null or empty buffer to an empty collection" in {
      val listCodec = Codec[List[Int]]
      roundTrip(listCodec, null) shouldBe List.empty[Int]
      roundTrip(listCodec, List.empty[Int]) shouldBe List.empty[Int]

      val setCodec = Codec[Set[Int]]
      roundTrip(setCodec, null) shouldBe Set.empty[Int]
      roundTrip(setCodec, Set.empty[Int]) shouldBe Set.empty[Int]

      val mapCodec = Codec[Map[String, Int]]
      roundTrip(mapCodec, null) shouldBe Map.empty[String, Int]
      roundTrip(mapCodec, Map.empty[String, Int]) shouldBe Map.empty[String, Int]
    }

    "round-trip None (and a null Option) to None" in {
      val codec = Codec[Option[Int]]
      roundTrip(codec, None) shouldBe None
      roundTrip(codec, null.asInstanceOf[Option[Int]]) shouldBe None
      roundTrip(codec, Some(0)) shouldBe Some(0)
    }

    "round-trip an empty string" in {
      roundTrip(Codec[String], "") shouldBe ""
    }

    "decode a null UDT buffer to null" in {
      val codec = Codec.of[Person]()
      roundTrip(codec, null.asInstanceOf[Person]) shouldBe null
    }
  }
}

object CodecRoundTripPropertySpec {

  final case class Person(
      name: String,
      age: Int,
      active: Boolean,
      nickname: Option[String],
      coords: (Int, Int)
  )

  implicit val arbPerson: Arbitrary[Person] = {
    // Reuse the ASCII-safe string generator so nested String fields round-trip.
    val safeString = Gen.asciiPrintableStr
    // A Some("") field would collapse to None on decode (empty buffer == NULL),
    // so the optional field uses non-empty strings; that collapse is pinned by
    // its own case in the Option section.
    val nonEmptyString = safeString.map(s => if (s.isEmpty) "x" else s)
    Arbitrary(
      for {
        name <- safeString
        age <- Arbitrary.arbitrary[Int]
        active <- Arbitrary.arbitrary[Boolean]
        nickname <- Gen.option(nonEmptyString)
        x <- Arbitrary.arbitrary[Int]
        y <- Arbitrary.arbitrary[Int]
      } yield Person(name, age, active, nickname, (x, y))
    )
  }
}
