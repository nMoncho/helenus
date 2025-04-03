/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import net.nmoncho.helenus.api.RowMapperDerivationSpec.IceCream
import net.nmoncho.helenus.api.RowMapperDerivationSpec.IceCreamWithSpecialProps
import net.nmoncho.helenus.api.RowMapperDerivationSpec.IceCreamWithSpecialPropsAsTuple
import net.nmoncho.helenus.api.RowMapperDerivationSpec.RenamedIceCream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RowMapperDerivationSpec extends AnyWordSpec with Matchers {
  import net.nmoncho.helenus._

  "RowMapper" should {
    "semi-auto derive on companion object" in {
      IceCream.rowMapper should not be null

      withClue("and should be implicitly available, and not be derived twice") {
        implicitly[RowMapper[IceCream]] shouldBe IceCream.rowMapper
      }
    }

    "produce instances for tuples" in {
      RowMapper[(String, Int)] should not be null

      withClue("and should be implicitly available") {
        implicitly[RowMapper[(String, Int)]] should not be null
      }
    }

    "produce instances for simple types" in {
      RowMapper[String] should not be null

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

}

object RowMapperDerivationSpec {

  import net.nmoncho.helenus._
  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  object IceCream {
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
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
      RowMapper[IceCreamWithSpecialProps]
  }

  case class IceCreamWithSpecialPropsAsTuple(name: String, props: (Int, Boolean))
  object IceCreamWithSpecialPropsAsTuple {
    implicit val rowMapper: RowMapper[IceCreamWithSpecialPropsAsTuple] =
      RowMapper[IceCreamWithSpecialPropsAsTuple]
  }

  case class RenamedIceCream(naam: String, kers: Int, hoorn: Boolean)

  object RenamedIceCream {
    implicit val rowMapper: RowMapper[RenamedIceCream] = RowMapper
      .renamed[RenamedIceCream](_.naam -> "name", _.kers -> "numCherries", _.hoorn -> "cone")
  }
}
