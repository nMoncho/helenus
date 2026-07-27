/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import scala.annotation.unused

/** Witnesses that `Column.contains` accepts a value of type `V` for a column
  * of type `T`, mirroring CQL's own overloading of `CONTAINS`: for a
  * collection (`Set[V]`, `List[V]`) it is the element type; for a map
  * (`Map[K, V]`) it is the value type — CQL's `m CONTAINS v` checks
  * membership among the map's VALUES, distinct from `containsKey` (its keys)
  * and `entry` (a specific key/value pair).
  */
sealed trait ContainsValue[T, V]

object ContainsValue extends LowPriorityContainsValue {

  /** A map's `contains` checks its VALUES. */
  implicit def map[K, V]: ContainsValue[Map[K, V], V] = new ContainsValue[Map[K, V], V] {}
}

sealed trait LowPriorityContainsValue {

  /** Any other collection's `contains` checks its elements. */
  implicit def iterable[T, V](implicit @unused ev: T <:< Iterable[V]): ContainsValue[T, V] =
    new ContainsValue[T, V] {}
}
