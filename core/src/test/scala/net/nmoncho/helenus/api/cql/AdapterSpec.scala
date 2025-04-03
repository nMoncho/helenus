/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

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

      adapter(Hotels.h1) should equal(
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
    }

    "adapt a case class with a computed column" in {
      val adapter = Adapter
        .builder[Hotel]
        .withComputedColumn(_.name.charAt(0))
        .build

      adapter(Hotels.h1) should equal(
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
        Set(rotterdamErasmusBridge.name, rotterdamZoo.name),
        'T' // computed column
      )
    }
  }
}
