/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package utils

import java.time.LocalDate

import scala.util.Random

import com.datastax.oss.driver.api.core.CqlSession

object HotelsTestData {

  import net.nmoncho.helenus.models._

  private val rnd = new Random(0)

  def insertTestData()(implicit session: CqlSession): Unit = {
    val insertHotelByPOI =
      """INSERT INTO hotels_by_poi(poi_name, hotel_id, name, phone, address)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
        .prepare[String, String, String, String, Address]
    val insertPoiByHotel =
      """INSERT INTO pois_by_hotel(poi_name, hotel_id, description)
        |VALUES (?, ?, ?)""".stripMargin.toCQL
        .prepare[String, String, String]

    for {
      hotel <- Hotels.all
      poiName <- hotel.pois
      poi <- PointOfInterests.all.find(_.name == poiName)
    } {
      insertHotelByPOI.execute(poiName, hotel.id, hotel.name, hotel.phone, hotel.address)
      insertPoiByHotel.execute(poiName, hotel.id, poi.description)
    }

    val insertHotel =
      """INSERT INTO hotels(id, name, phone, address, pois)
        |VALUES (?, ?, ?, ?, ?)""".stripMargin.toCQL
        .prepare[String, String, String, Address, Set[String]]
    Hotels.all.foreach(h => insertHotel.execute(h.id, h.name, h.phone, h.address, h.pois))

    val insertAvailableRooms =
      """INSERT INTO available_rooms_by_hotel_date(hotel_id, date, room_number, is_available)
        |VALUES (?, ?, ?, ?)""".stripMargin.toCQL
        .prepare[String, LocalDate, Short, Boolean]
    for {
      (hotel, rooms) <- Hotels.availableRooms
      room <- 0 until rooms
      january1st = LocalDate.parse("2023-01-01")
      date <- (0 to 31).map(days => january1st.plusDays(days))
    } insertAvailableRooms.execute(
      hotel.id,
      date,
      room.toShort,
      // even room are available on even days of the month
      date.getDayOfMonth % 2 == room % 2
    )

    val insertAmenities =
      """INSERT INTO amenities_by_room(hotel_id, room_number, amenity_name, description)
        |VALUES (?, ?, ?, ?)""".stripMargin.toCQL
        .prepare[String, Short, String, String]
    val amentiesSize = Amenities.all.size
    val amenities    = Amenities.all.toVector

    for {
      (hotel, rooms) <- Hotels.availableRooms
      room <- 0 until rooms
      amenityAmount = rnd.nextInt(amentiesSize) + 1
      amenity <- (0 until amenityAmount).map(amenities.apply)
    } insertAmenities.execute(hotel.id, room.toShort, amenity.name, amenity.description)
  }

  object PointOfInterests {

    final val rotterdamErasmusBridge: PointOfInterest = PointOfInterest(
      name        = "Erasmus Bridge",
      description = "Iconic cable-stayed bridge in Rotterdam."
    )

    final val rotterdamZoo: PointOfInterest = PointOfInterest(
      name        = "Rotterdam Zoo",
      description = "Zoo located in the city center of Rotterdam."
    )

    final val rotterdamMarkthal: PointOfInterest = PointOfInterest(
      name        = "Markthal Rotterdam",
      description = "Market hall in Rotterdam with a distinctive arched roof."
    )

    final val rotterdamEuromast: PointOfInterest = PointOfInterest(
      name        = "Euromast",
      description = "Tall observation tower in Rotterdam with panoramic views of the city."
    )

    final val rotterdamBoijmans: PointOfInterest = PointOfInterest(
      name = "Museum Boijmans Van Beuningen",
      description =
        "Museum in Rotterdam featuring art and design from the Middle Ages to the present day."
    )

    val all: Set[PointOfInterest] = Set(
      rotterdamErasmusBridge,
      rotterdamZoo,
      rotterdamMarkthal,
      rotterdamEuromast,
      rotterdamBoijmans
    )
  }

  object Hotels {

    import PointOfInterests._

    final val h1: Hotel = Hotel(
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

    final val h2: Hotel = Hotel(
      "h2",
      "Rotterdam Marriott Hotel",
      "+31 10 710 1515",
      Address(
        "Weena 686",
        "Rotterdam",
        "South Holland",
        "3012 CN",
        "Netherlands"
      ),
      Set(rotterdamErasmusBridge.name, rotterdamZoo.name, rotterdamMarkthal.name)
    )

    final val h3: Hotel = Hotel(
      "h3",
      "Mainport Hotel Rotterdam",
      "+31 10 217 6666",
      Address(
        "Leuvehaven 77",
        "Rotterdam",
        "South Holland",
        "3011 EA",
        "Netherlands"
      ),
      Set(rotterdamErasmusBridge.name, rotterdamZoo.name, rotterdamMarkthal.name)
    )

    final val h4: Hotel = Hotel(
      "h4",
      "The New York Hotel Rotterdam",
      "+31 10 217 3000",
      Address(
        "Meent 78-82",
        "Rotterdam",
        "South Holland",
        "3011 JM",
        "Netherlands"
      ),
      Set(rotterdamErasmusBridge.name, rotterdamZoo.name, rotterdamMarkthal.name)
    )

    final val h5: Hotel = Hotel(
      "h5",
      "CitizenM Rotterdam",
      "+31 10 717 9999",
      Address(
        "Wilhelminakade 137",
        "Rotterdam",
        "South Holland",
        "3072 AP",
        "Netherlands"
      ),
      Set(rotterdamErasmusBridge.name, rotterdamZoo.name, rotterdamEuromast.name)
    )

    val all: Seq[Hotel] = Seq(
      h1,
      h2,
      h3,
      h4,
      h5
    )

    val availableRooms: Map[Hotel, Short] = Map(
      h1 -> 8,
      h2 -> 5,
      h3 -> 10,
      h4 -> 6,
      h5 -> 7
    )
  }

  object Amenities {
    final val freeBreakfast: Amenity =
      Amenity("Free breakfast", "Complimentary breakfast served daily.")
    final val fitnessCenter: Amenity = Amenity(
      "Fitness center",
      "On-site fitness center with treadmills, weight machines, and free weights."
    )
    final val swimmingPool: Amenity =
      Amenity("Swimming pool", "Outdoor swimming pool open seasonally.")
    final val spa: Amenity =
      Amenity("Spa", "On-site spa offering a variety of massage and beauty treatments.")
    final val businessCenter: Amenity =
      Amenity("Business center", "Business center with computers, printers, and a fax machine.")
    final val freeWifi: Amenity = Amenity(
      "Free Wi-Fi",
      "Complimentary high-speed Wi-Fi access in all guest rooms and public areas."
    )
    final val airCo: Amenity   = Amenity("Air conditioning", "Air conditioning in all guest rooms.")
    final val laundry: Amenity = Amenity("Laundry facilities", "On-site guest laundry facilities.")
    final val freeParking: Amenity =
      Amenity("Free parking", "Complimentary self-parking in the hotel's lot.")
    final val roomService: Amenity =
      Amenity("Room service", "Room service available 24 hours a day.")

    val all: Seq[Amenity] = Seq(
      freeBreakfast,
      fitnessCenter,
      swimmingPool,
      spa,
      businessCenter,
      freeWifi,
      airCo,
      laundry,
      freeParking,
      roomService
    )
  }
}
