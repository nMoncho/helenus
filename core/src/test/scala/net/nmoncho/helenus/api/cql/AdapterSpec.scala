/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import net.nmoncho.helenus.api.cql.AdapterSpec.Person
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AdapterSpec extends AnyWordSpec with Matchers {

  import HotelsTestData.PointOfInterests._
  import HotelsTestData._

  "Adapter" should {
    "adapt a case class to a tuple" in {
      val adapter = Adapter[Hotel]

      // Binding to an explicit tuple type pins the derived output arity and
      // element types, not just the values.
      val adapted: (String, String, String, Address, Set[String]) = adapter(Hotels.h1)

      adapted shouldBe (
        (
          "h1",
          "The James Rotterdam",
          "+31 10 710 9000",
          Address(
            "Wijnhaven 107",
            "Rotterdam",
            "South Holland",
            "3011 WN",
            "Netherlands"
          ),
          Set(rotterdamErasmusBridge.name, rotterdamZoo.name)
        )
      )
    }

    "adapt a small case class to a tuple" in {
      val adapter = Adapter[Person]

      val adapted: (String, Int) = adapter(Person("Alice", 30))

      adapted shouldBe (("Alice", 30))
    }

    "build an adapter without computed columns equal to the no-op adapter" in {
      val built = Adapter.builder[Hotel].build

      built(Hotels.h1) shouldBe Adapter[Hotel].apply(Hotels.h1)
    }

    "adapt a case class with a computed column" in {
      val adapter = Adapter
        .builder[Hotel]
        .withComputedColumn(_.name.charAt(0))
        .build

      val adapted: (String, String, String, Address, Set[String], Char) = adapter(Hotels.h1)

      adapted shouldBe (
        (
          "h1",
          "The James Rotterdam",
          "+31 10 710 9000",
          Hotels.h1.address,
          Set(rotterdamErasmusBridge.name, rotterdamZoo.name),
          'T' // computed column
        )
      )
    }

    "adapt a case class with a computed column derived from several fields" in {
      val adapter = Adapter
        .builder[Person]
        .withComputedColumn(p => s"${p.name}:${p.age}")
        .build

      val adapted: (String, Int, String) = adapter(Person("Alice", 30))

      adapted shouldBe (("Alice", 30, "Alice:30"))
    }

    "append multiple computed columns in the order they are declared" in {
      val adapter = Adapter
        .builder[Hotel]
        .withComputedColumn(_.name.charAt(0))
        .withComputedColumn(_.pois.size)
        .withComputedColumn(_.phone.startsWith("+"))
        .build

      val adapted: (String, String, String, Address, Set[String], Char, Int, Boolean) =
        adapter(Hotels.h1)

      adapted shouldBe (
        (
          "h1",
          "The James Rotterdam",
          "+31 10 710 9000",
          Hotels.h1.address,
          Hotels.h1.pois,
          'T', // first computed column
          2, // second computed column: number of POIs
          true // third computed column: phone is international
        )
      )
    }
  }
}

object AdapterSpec {
  final case class Person(name: String, age: Int)
}
