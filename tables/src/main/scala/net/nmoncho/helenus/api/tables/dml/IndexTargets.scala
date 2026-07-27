/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

/** The CQL index target(s) that must be declared on a column of type `T` so
  * its index-aware predicates (`contains`, and for a map `containsKey` /
  * `entry`) can be satisfied by CQL without `allowFiltering`: a
  * `CREATE INDEX ON t (col)` indexes a collection's VALUES (what `contains`
  * needs), but a map's KEYS need a separate `CREATE INDEX ON t (KEYS(col))`
  * for `containsKey`, and its ENTRIES need `CREATE INDEX ON t (ENTRIES(col))`
  * for `entry` (`col[key] = value`). Every other column type needs only the
  * plain column index — EXCEPT a [[Frozen]] collection, which is serialized
  * as a single value and so can only be indexed with `FULL(col)` (no
  * per-element KEYS / VALUES / ENTRIES indexing once frozen).
  *
  * Each target is paired with a suffix used to keep its generated index name
  * distinct from the others.
  */
sealed trait IndexTargets[T] {
  def targets(columnName: String): List[(String, String)]
}

object IndexTargets extends LowPriorityIndexTargets {

  /** A map needs a values index (`contains`), a keys index (`containsKey`), and an entries index (`entry`). */
  implicit def map[K, V]: IndexTargets[Map[K, V]] = new IndexTargets[Map[K, V]] {
    def targets(columnName: String): List[(String, String)] =
      List(
        "idx" -> columnName,
        "keys_idx" -> s"KEYS($columnName)",
        "entries_idx" -> s"ENTRIES($columnName)"
      )
  }

  /** A frozen collection (of any kind) can only use a FULL index. */
  implicit def frozen[T]: IndexTargets[Frozen[T]] = new IndexTargets[Frozen[T]] {
    def targets(columnName: String): List[(String, String)] =
      List("full_idx" -> s"FULL($columnName)")
  }
}

sealed trait LowPriorityIndexTargets {

  /** Everything else (scalars, sets, lists) needs only the plain column index. */
  implicit def default[T]: IndexTargets[T] = new IndexTargets[T] {
    def targets(columnName: String): List[(String, String)] =
      List("idx" -> columnName)
  }
}
