/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml

import shapeless.::
import shapeless.HList
import shapeless.HNil

private[cql] object Binding {

  /** Runtime view of an HList of bound arguments, in order. */
  def values(l: HList): List[Any] = l match {
    case HNil => Nil
    case head :: tail => head :: values(tail)
  }
}
