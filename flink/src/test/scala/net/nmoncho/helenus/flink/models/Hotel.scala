/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink.models

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import org.apache.flink.api.common.typeinfo.TypeInformation

final case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
) {
  def this() = this("", "", "", "", "")
}

object Address {
  implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()
}

final case class Hotel(id: String, name: String, phone: String, address: Address) {
  def this() = this("", "", "", new Address())
}

object Hotel {

  import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._

  implicit val rowMapper: RowMapper[Hotel] = new RowMapper[Hotel] {
    override def apply(row: Row): Hotel = Hotel(
      id      = row.getCol[String]("id"),
      name    = row.getCol[String]("name"),
      phone   = row.getCol[String]("phone"),
      address = row.getCol[Address]("address")
    )
  }

  //  implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]

  implicit val adapter: Adapter[Hotel, (String, String, String, Address)] =
    Adapter[Hotel]

  implicit val addressTypeInformation: TypeInformation[Address] = Pojo[Address]
  implicit val hotelTypeInformation: TypeInformation[Hotel]     = Pojo[Hotel]
}
