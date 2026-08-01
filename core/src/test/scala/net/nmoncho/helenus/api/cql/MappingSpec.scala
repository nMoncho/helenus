/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MappingSpec extends AnyWordSpec with Matchers with CassandraSpec {

  import HotelsTestData._
  import MappingSpec._

  private implicit lazy val cqlSession: CqlSession = session

  "Mapping" should {
    "bind all case class fields as query parameters" in {
      implicit val mapping: Mapping[Hotel] = Mapping[Hotel]()

      val insert = insertHotel
      insert.execute(Hotels.h1)

      val row = hotelRow(Hotels.h1.id).value
      row.getString("id") shouldBe Hotels.h1.id
      row.getString("name") shouldBe Hotels.h1.name
      row.getString("phone") shouldBe Hotels.h1.phone
      row.getUdtValue("address").getString("street") shouldBe Hotels.h1.address.street
      row.getSet("pois", classOf[String]).asScala should contain theSameElementsAs Hotels.h1.pois
    }

    "map a row back into a case class" in {
      val mapping: Mapping[Hotel] = Mapping[Hotel]()

      insertHotel(mapping).execute(Hotels.h1)

      mapping(hotelRow(Hotels.h1.id).value) shouldBe Hotels.h1
    }

    "map fields to columns using the implicit ColumnNamingScheme" in {
      val hotel = SnakeHotel(Hotels.h1.id, Hotels.h1.name, Hotels.h1.phone)

      insertSnakeHotel.execute(hotel)

      // `phoneNumber` is not renamed, so it's mapped with the SnakeCase naming scheme
      snakeHotelRow(hotel.id).value.getString("phone_number") shouldBe hotel.phoneNumber
    }

    "map renamed fields to their columns" in {
      val hotel = SnakeHotel(Hotels.h1.id, Hotels.h1.name, Hotels.h1.phone)

      insertSnakeHotel.execute(hotel)

      val row = snakeHotelRow(hotel.id).value
      // `id` is renamed with a constant, whereas `name` is renamed with a literal
      row.getString("hotel_id") shouldBe hotel.id
      row.getString("hotel_name") shouldBe hotel.name

      withClue("and reads them back") {
        SnakeHotel.mapping(row) shouldBe hotel
      }
    }

    "ignore case class fields that aren't query parameters" in {
      implicit val mapping: Mapping[Hotel] = Mapping[Hotel]()

      insertHotel(mapping).execute(Hotels.h1)

      // only `id` is bound, the rest of the fields are ignored
      val query = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]

      Option(query.execute(Hotels.h1).one()).value.getString("name") shouldBe Hotels.h1.name
    }

    "map from something, and then get something else mapped out as well" in {
      implicit val mapping: Mapping[Hotel] = Mapping[Hotel]()

      insertHotel(mapping).execute(Hotels.h1)

      val queryName = "SELECT name FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]
        .as[String]
      Option(queryName.execute(Hotels.h1).one()).value shouldBe Hotels.h1.name

      val queryIdName = "SELECT id, name FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]
        .as[(String, String)]
      Option(queryIdName.execute(Hotels.h1).one()).value shouldBe (Hotels.h1.id, Hotels.h1.name)
    }

    "map from something, and to the same thing" in {
      implicit val mapping: Mapping[Hotel] = Mapping[Hotel]()

      insertHotel(mapping).execute(Hotels.h1)

      val queryName = "SELECT * FROM hotels WHERE id = ?".toCQL
        .prepareFrom[Hotel]
        .as[Hotel]
      Option(queryName.execute(Hotels.h1).one()).value shouldBe Hotels.h1
    }

    "not bind null fields by default" in {
      val mapping = Mapping[Hotel]()
      val insert  = insertHotel(mapping)

      insert.execute(Hotels.h1)
      insert.execute(Hotels.h1.copy(name = null))

      // `name` was left unset, which is not the same as setting it to `null`
      hotelRow(Hotels.h1.id).value.getString("name") shouldBe Hotels.h1.name
    }

    "bind null fields when they are not ignored" in {
      val mapping = Mapping[Hotel]()
      val insert  = insertHotel(mapping).withIgnoreNullFields(ignore = false)

      insert.execute(Hotels.h1)
      insert.execute(Hotels.h1.copy(name = null))

      hotelRow(Hotels.h1.id).value.getString("name") shouldBe null
    }

    "bind computed columns" in {
      implicit val mapping: Mapping[ComputedHotel] = Mapping[ComputedHotel]()
        .withComputedColumn("name_length", (hotel: ComputedHotel) => hotel.name.length)

      val hotel = ComputedHotel(Hotels.h1.id, Hotels.h1.name)

      """INSERT INTO mapping_computed(id, name, name_length)
        |VALUES (?, ?, ?)""".stripMargin.toCQL
        .prepareFrom[ComputedHotel]
        .execute(hotel)

      val row = computedHotelRow(hotel.id).value
      row.getString("name") shouldBe hotel.name
      row.getInt("name_length") shouldBe hotel.name.length
    }

    "ignore computed columns that aren't query parameters" in {
      implicit val mapping: Mapping[ComputedHotel] = Mapping[ComputedHotel]()
        .withComputedColumn("name_length", (hotel: ComputedHotel) => hotel.name.length)

      val hotel = ComputedHotel(Hotels.h1.id, Hotels.h1.name)

      "INSERT INTO mapping_computed(id, name) VALUES (?, ?)".toCQL
        .prepareFrom[ComputedHotel]
        .execute(hotel)

      val row = computedHotelRow(hotel.id).value
      row.getString("name") shouldBe hotel.name
      row.isNull("name_length") shouldBe true
    }

    "override a computed column that is defined twice" in {
      implicit val mapping: Mapping[ComputedHotel] = Mapping[ComputedHotel]()
        .withComputedColumn("name_length", (hotel: ComputedHotel) => hotel.name.length)
        .withComputedColumn("name_length", (_: ComputedHotel) => 42)

      val hotel = ComputedHotel(Hotels.h1.id, Hotels.h1.name)

      """INSERT INTO mapping_computed(id, name, name_length)
        |VALUES (?, ?, ?)""".stripMargin.toCQL
        .prepareFrom[ComputedHotel]
        .execute(hotel)

      computedHotelRow(hotel.id).value.getInt("name_length") shouldBe 42
    }

    "let computed columns override case class fields" in {
      implicit val mapping: Mapping[ComputedHotel] = Mapping[ComputedHotel]()
        .withComputedColumn("name", (_: ComputedHotel) => "computed name")

      val hotel = ComputedHotel(Hotels.h1.id, Hotels.h1.name)

      "INSERT INTO mapping_computed(id, name) VALUES (?, ?)".toCQL
        .prepareFrom[ComputedHotel]
        .execute(hotel)

      computedHotelRow(hotel.id).value.getString("name") shouldBe "computed name"
    }

    "prepare a statement even when query parameters are missing from the case class" in {
      implicit val mapping: Mapping[Hotel] = Mapping[Hotel]()

      // 'poi_name' and 'hotel_id' cannot be bound from a `Hotel`, which is only logged as an error
      noException should be thrownBy {
        """INSERT INTO hotels_by_poi(poi_name, hotel_id, name, phone, address)
          |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
          .prepareFrom[Hotel]
          .apply(Hotels.h1)
      }
    }
  }

  private def insertHotel(implicit mapping: Mapping[Hotel]) =
    """INSERT INTO hotels(id, name, phone, address, pois)
      |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
      .prepareFrom[Hotel]

  private def insertSnakeHotel =
    """INSERT INTO mapping_snake_case(hotel_id, hotel_name, phone_number)
      |VALUES (?, ?, ?)""".stripMargin.toCQL
      .prepareFrom[SnakeHotel]

  private def hotelRow(id: String) =
    Option(execute(s"SELECT * FROM hotels WHERE id = '$id'").one())

  private def snakeHotelRow(id: String) =
    Option(execute(s"SELECT * FROM mapping_snake_case WHERE hotel_id = '$id'").one())

  private def computedHotelRow(id: String) =
    Option(execute(s"SELECT * FROM mapping_computed WHERE id = '$id'").one())

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")

    executeDDL("""CREATE TABLE IF NOT EXISTS mapping_snake_case(
                 |  hotel_id      TEXT PRIMARY KEY,
                 |  hotel_name    TEXT,
                 |  phone_number  TEXT
                 |)""".stripMargin)

    executeDDL("""CREATE TABLE IF NOT EXISTS mapping_computed(
                 |  id          TEXT PRIMARY KEY,
                 |  name        TEXT,
                 |  name_length INT
                 |)""".stripMargin)
  }

}

object MappingSpec {

  /** Column names defined as constants, to exercise renaming fields without a literal */
  object Columns {
    final val hotelId: String = "hotel_id"
  }

  final case class SnakeHotel(id: String, name: String, phoneNumber: String)

  object SnakeHotel {
    implicit val namingScheme: ColumnNamingScheme = ColumnNamingScheme.SnakeCase

    implicit val mapping: Mapping[SnakeHotel] =
      Mapping[SnakeHotel](_.id -> Columns.hotelId, _.name -> "hotel_name")
  }

  final case class ComputedHotel(id: String, name: String)

}
