/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api

import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.HotelsTestData.Hotels
import net.nmoncho.helenus.utils.TestRow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for [[RowMapper]] derivation and mapping.
  *
  * These don't need a live Cassandra connection: the mapping logic only reads
  * columns out of a [[Row]], so every test runs against a mocked row (see
  * [[TestRow]]). The end-to-end integration against a genuine driver row lives
  * in `RowMapperCassandraSpec`.
  */
class RowMapperSpec extends AnyWordSpec with Matchers {

  import RowMapperSpec._

  "RowMapper derivation" should {
    "semi-auto derive on companion object" in {
      IceCream.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[RowMapper[IceCream]] shouldBe IceCream.rowMapper
      }
    }

    "produce instances for tuples" in {
      RowMapper.of[(String, Int)] should not be null

      withClue("and should be implicitly available") {
        implicitly[RowMapper[(String, Int)]] should not be null
      }
    }

    "produce instances for simple types" in {
      RowMapper.of[String] should not be null

      withClue("and should be implicitly available") {
        implicitly[RowMapper[String]] should not be null
      }
    }

    "semi-auto derive using a custom ColumnMapper" in {
      IceCreamWithSpecialProps.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[RowMapper[IceCreamWithSpecialProps]] shouldBe IceCreamWithSpecialProps.rowMapper
      }
    }

    "semi-auto derive with a tuple field" in {
      IceCreamWithSpecialPropsAsTuple.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[
          RowMapper[IceCreamWithSpecialPropsAsTuple]
        ] shouldBe IceCreamWithSpecialPropsAsTuple.rowMapper
      }
    }

    "semi-auto derive on companion object with renamed mapping" in {
      RenamedIceCream.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[RowMapper[RenamedIceCream]] shouldBe RenamedIceCream.rowMapper
      }
    }
  }

  "RowMapper" should {
    "map a single-column result to a simple type" in {
      // A simple type reads the first column, by index
      RowMapper.of[String].apply(TestRow("name" -> Hotels.h1.name)) shouldBe Hotels.h1.name
    }

    "map a result to a tuple" in {
      // Tuples read their elements by index, in order
      RowMapper
        .of[(String, String)]
        .apply(TestRow("name" -> Hotels.h1.name, "phone" -> Hotels.h1.phone)) shouldBe
      (Hotels.h1.name -> Hotels.h1.phone)
    }

    "map a result to a case class using the derived mapper" in {
      Hotel.rowMapper.apply(rowFor(Hotels.h1)) shouldBe Hotels.h1
    }

    "map a result to a case class using an explicit mapper (columns by name)" in {
      val mapper: RowMapper[Hotel] = (row: Row) =>
        Hotel(
          row.getCol[String]("id"),
          row.getCol[String]("name"),
          row.getCol[String]("phone"),
          row.getCol[Address]("address"),
          row.getCol[Set[String]]("pois")
        )

      mapper.apply(rowFor(Hotels.h1)) shouldBe Hotels.h1
    }

    "map a result to a case class using an explicit mapper (columns by index)" in {
      val mapper: RowMapper[Hotel] = (row: Row) =>
        Hotel(
          row.getCol[String](0),
          row.getCol[String](1),
          row.getCol[String](2),
          row.getCol[Address](3),
          row.getCol[Set[String]](4)
        )

      mapper.apply(rowFor(Hotels.h1)) shouldBe Hotels.h1
    }

    "map a result using a semi-auto derived mapper" in {
      IceCream.rowMapper.apply(
        TestRow("name" -> "Vanilla", "numCherries" -> 3, "cone" -> true)
      ) shouldBe IceCream("Vanilla", 3, cone = true)
    }

    "map a result using a custom ColumnMapper" in {
      IceCreamWithSpecialProps.rowMapper.apply(
        TestRow("name" -> "Vanilla", "numCherries" -> 3, "cone" -> true)
      ) shouldBe IceCreamWithSpecialProps("Vanilla", SpecialProps(3, cone = true))
    }

    "map a result honoring renamed mappings" in {
      RenamedIceCream.rowMapper.apply(
        TestRow("name" -> "Vanilla", "numCherries" -> 3, "cone" -> true)
      ) shouldBe RenamedIceCream("Vanilla", 3, hoorn = true)
    }

    "map a result with an Either field to different columns" in {
      case class Hotel2(
          id: String,
          name: String,
          phoneOrAddress: Either[String, Address],
          pois: Set[String]
      )

      implicit val phoneOrAddressColMapper: ColumnMapper[Either[String, Address]] =
        ColumnMapper.either[String, Address]("phone", "address")
      val mapper: RowMapper[Hotel2] = RowMapper[Hotel2]()

      val hotel = Hotels.h3

      withClue("resolving to Right when the left column is null: ") {
        val row = TestRow(
          "id" -> hotel.id,
          "name" -> hotel.name,
          "phone" -> null,
          "address" -> hotel.address,
          "pois" -> hotel.pois
        )

        mapper.apply(row).phoneOrAddress shouldBe Right(hotel.address)
      }

      withClue("resolving to Left when the right column is null: ") {
        val row = TestRow(
          "id" -> hotel.id,
          "name" -> hotel.name,
          "phone" -> hotel.phone,
          "address" -> null,
          "pois" -> hotel.pois
        )

        mapper.apply(row).phoneOrAddress shouldBe Left(hotel.phone)
      }
    }
  }

  /** Builds a row exposing every [[Hotel]] column, both by name and by index. */
  private def rowFor(hotel: Hotel): Row = TestRow(
    "id" -> hotel.id,
    "name" -> hotel.name,
    "phone" -> hotel.phone,
    "address" -> hotel.address,
    "pois" -> hotel.pois
  )
}

object RowMapperSpec {

  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  object IceCream {
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]()
  }

  case class SpecialProps(numCherries: Int, cone: Boolean)
  object SpecialProps {
    implicit val columnMapper: ColumnMapper[SpecialProps] = (_: String, row: Row) =>
      SpecialProps(
        row.getInt("numCherries"),
        row.getBoolean("cone")
      )
  }
  case class IceCreamWithSpecialProps(name: String, props: SpecialProps)
  object IceCreamWithSpecialProps {
    implicit val rowMapper: RowMapper[IceCreamWithSpecialProps] =
      RowMapper[IceCreamWithSpecialProps]()
  }

  case class IceCreamWithSpecialPropsAsTuple(name: String, props: (Int, Boolean))
  object IceCreamWithSpecialPropsAsTuple {
    implicit val rowMapper: RowMapper[IceCreamWithSpecialPropsAsTuple] =
      RowMapper[IceCreamWithSpecialPropsAsTuple]()
  }

  case class RenamedIceCream(naam: String, kers: Int, hoorn: Boolean)

  object RenamedIceCream {
    implicit val rowMapper: RowMapper[RenamedIceCream] =
      RowMapper[RenamedIceCream](_.naam -> "name", _.kers -> "numCherries", _.hoorn -> "cone")
  }
}
