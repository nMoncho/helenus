/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
  implicit val addressCodec: TypeCodec[Address] = Codec.udtOf[Address]()
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
