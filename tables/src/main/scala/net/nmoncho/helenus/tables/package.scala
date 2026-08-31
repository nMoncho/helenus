/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

/** Parity entry point for the Tables DSL.
  *
  * The streaming modules are imported as `net.nmoncho.helenus.<module>` (for example
  * `net.nmoncho.helenus.zio`); this package gives the Tables DSL the same shape, so it can be
  * brought in with `import net.nmoncho.helenus.tables._` alongside `import net.nmoncho.helenus._`.
  *
  * The members here forward to [[net.nmoncho.helenus.api.tables]], which remains available and
  * holds the full set of public types. `HList`, `HNil`, and `::` are the library's own vocabulary
  * for column lists and type-level keys (aliases over shapeless, an implementation detail), so user
  * code does not import shapeless directly.
  */
package object tables {

  /** Describe a table as a case class; see [[net.nmoncho.helenus.api.tables.Table]]. */
  type Table[A] = net.nmoncho.helenus.api.tables.Table[A]

  type HList = net.nmoncho.helenus.api.tables.HList

  type HNil = net.nmoncho.helenus.api.tables.HNil
  val HNil = net.nmoncho.helenus.api.tables.HNil

  type ::[+H, +T <: HList] = net.nmoncho.helenus.api.tables.::[H, T]
  val :: = net.nmoncho.helenus.api.tables.::

  /** Bind-marker placeholder used to leave a hole in a statement for a prepared parameter. */
  final val ? = net.nmoncho.helenus.api.tables.?

  /** Group several INSERT / UPDATE / DELETE statements into one CQL `BATCH`;
    * see [[net.nmoncho.helenus.api.tables.dml.Batch]].
    */
  type Batch[Param <: HList] = net.nmoncho.helenus.api.tables.Batch[Param]
  val Batch = net.nmoncho.helenus.api.tables.Batch
}
