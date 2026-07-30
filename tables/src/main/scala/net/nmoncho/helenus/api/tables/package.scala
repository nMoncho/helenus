/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

package object tables {

  type HNil = shapeless.HNil
  val HNil = shapeless.HNil

  type ::[+H, +T <: shapeless.HList] = shapeless.::[H, T]
  val :: = shapeless.::

  final val ? = net.nmoncho.helenus.api.tables.dml.?
}
