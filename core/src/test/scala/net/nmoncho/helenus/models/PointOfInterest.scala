/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.models

import net.nmoncho.helenus.api.RowMapper

final case class PointOfInterest(name: String, description: String)

object PointOfInterest {
  import net.nmoncho.helenus._

  implicit val rowMapper: RowMapper[PointOfInterest] = RowMapper[PointOfInterest]
}
