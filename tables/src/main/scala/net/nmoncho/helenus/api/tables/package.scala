/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

/** The Tables DSL is also available at the parity namespace
  * [[net.nmoncho.helenus.tables]], matching how the streaming modules are imported.
  *
  * The `HList`, `HNil`, and `::` members below are the library's own vocabulary for building
  * column lists and type-level keys. They are aliases over shapeless (an implementation detail),
  * so user code does not need to import shapeless directly.
  */
package object tables {

  type HList = shapeless.HList

  type HNil = shapeless.HNil
  val HNil = shapeless.HNil

  type ::[+H, +T <: HList] = shapeless.::[H, T]
  val :: = shapeless.::

  final val ? = net.nmoncho.helenus.api.tables.dml.?

  /** Group several INSERT / UPDATE / DELETE statements into one CQL `BATCH`;
    * see [[net.nmoncho.helenus.api.tables.dml.Batch]].
    */
  type Batch[Params <: HList] = net.nmoncho.helenus.api.tables.dml.Batch[Params]
  val Batch = net.nmoncho.helenus.api.tables.dml.Batch
}
