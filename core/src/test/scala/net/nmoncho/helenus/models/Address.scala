/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.models

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.SnakeCase

final case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)

object Address {
  import net.nmoncho.helenus._
  implicit val namingScheme: ColumnNamingScheme = SnakeCase

  final val Empty: Address = Address("", "", "", "", "")

  implicit val typeCodec: TypeCodec[Address] = Codec.of[Address]()
}
