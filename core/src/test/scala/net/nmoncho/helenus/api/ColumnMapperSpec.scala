/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ColumnMapperSpec extends AnyWordSpec with Matchers {

  import ColumnMapperSpec._

  "ColumnMapper.default" should {
    "read a value from the named column using the codec" in {
      val row = mockRow(Map("name" -> "Helena"))

      ColumnMapper.default[String].apply("name", row) shouldBe "Helena"
    }

    "use the column name it is given at apply time" in {
      val row = mockRow(Map("age" -> 42))

      ColumnMapper.default[Int].apply("age", row) shouldBe 42
    }
  }

  "ColumnMapper.either" should {
    def mapper: ColumnMapper[Either[String, Int]] =
      ColumnMapper.either[String, Int]("left", "right")

    "return Right when only the right column is set" in {
      val row = mockRow(Map("right" -> 7)) // left absent => treated as null
      mapper.apply("ignored", row) shouldBe Right(7)
    }

    "return Left when only the left column is set" in {
      val row = mockRow(Map("left" -> "boom"))
      mapper.apply("ignored", row) shouldBe Left("boom")
    }

    "default to Right when both columns are set" in {
      val row = mockRow(Map("left" -> "boom", "right" -> 7))
      mapper.apply("ignored", row) shouldBe Right(7)
    }

    "ignore the column name passed to apply" in {
      val row = mockRow(Map("right" -> 7))
      mapper.apply("this-name-is-not-used", row) shouldBe Right(7)
    }
  }

  "ColumnMapper.of" should {
    "explode a case class into columns, honouring renamed fields" in {
      val row = mockRow(
        Map(
          "renamed" -> "hello", // bar, explicitly renamed
          "prefix_baz" -> 42 // baz, non-renamed field
        )
      )

      val mapper = ColumnMapper.of[Foo]("prefix_", _.bar -> "renamed")

      mapper.apply("ignored", row) shouldBe Foo("hello", 42)
    }
  }
}

object ColumnMapperSpec {

  final case class Foo(bar: String, baz: Int)

  /** Builds a mocked `Row` backed by a plain map.
    *
    * A key that is absent, or present with a `null` value, is reported as a
    * null column. `get` returns the mapped value regardless of the codec.
    */
  def mockRow(data: Map[String, Any]): Row = {
    val row = mock(classOf[Row])

    when(row.isNull(anyString())).thenAnswer { inv =>
      val col = inv.getArgument[String](0)
      !data.contains(col) || data(col) == null
    }

    when(row.get(anyString(), any[TypeCodec[Any]]())).thenAnswer { inv =>
      val col = inv.getArgument[String](0)
      data.getOrElse(col, null)
    }

    row
  }
}
