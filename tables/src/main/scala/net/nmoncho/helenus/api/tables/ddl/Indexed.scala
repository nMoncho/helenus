/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.tables.dml.BindMarker
import net.nmoncho.helenus.api.tables.dml.ContainsValue
import net.nmoncho.helenus.api.tables.dml.where._

/** Mixed into a [[Column]] by `Table.index` to record that a secondary
  * index covering `col.===` has been declared on it (a scalar column with
  * an ordinary secondary index, or a [[Frozen]] column with a FULL index).
  * Overrides [[Column.===]] to produce an [[IndexEqPredicate]] instead of
  * the usual [[EqPredicate]]: CQL can satisfy the restriction directly
  * through the index, so unlike the non-indexed case it does not force
  * `allowFiltering` (see [[PredicateShape]]).
  */
trait EqIndexed[T] { self: TableDef#Column[T] =>
  override def ===(value: T): IndexEqPredicate[Tag, T] =
    new IndexEqPredicate[Tag, T](self, value)

  override def ===(@unused m: BindMarker): IndexEqBindPredicate[Tag, T] =
    new IndexEqBindPredicate[Tag, T](this)
}

/** Mixed into a [[Column]] by `Table.index` to record that a VALUES index
  * covering `col.contains` has been declared on it (a collection's
  * elements, or a map's values). Overrides [[Column.contains]] to produce
  * an [[IndexPredicate]] instead of a plain [[Predicate]], exempting it
  * from `allowFiltering` (see [[PredicateShape]]).
  */
trait ValuesIndexed[T] { self: TableDef#Column[T] =>
  override def contains[V](
      value: V
  )(
      implicit @unused containsEv: ContainsValue[T, V],
      innerCodec: TypeCodec[V]
  ): IndexPredicate[Tag, T, V] =
    new IndexPredicate[Tag, T, V](self, "CONTAINS", value, innerCodec)

  override def contains[V](m: BindMarker)(
      implicit containsEv: ContainsValue[T, V],
      innerCodec: TypeCodec[V]
  ): IndexBindPredicate[Tag, T, V] =
    new IndexBindPredicate[Tag, T, V](self, "CONTAINS", innerCodec)
}

/** Mixed into a [[Column]] by `Table.index` to record that a KEYS index
  * covering `col.containsKey` has been declared on a map column. Overrides
  * [[Column.containsKey]] to produce an [[IndexPredicate]] instead of a
  * plain [[Predicate]], exempting it from `allowFiltering` (see
  * [[PredicateShape]]).
  */
trait KeysIndexed[T] { self: TableDef#Column[T] =>
  override def containsKey[K, V](value: K)(
      implicit ev: T <:< scala.collection.Map[K, V],
      innerCodec: TypeCodec[K]
  ): IndexPredicate[Tag, T, K] =
    new IndexPredicate[Tag, T, K](self, "CONTAINS KEY", value, innerCodec)

  override def containsKey[K, V](@unused m: BindMarker)(
      implicit @unused ev: T <:< scala.collection.Map[K, V],
      innerCodec: TypeCodec[K]
  ): IndexBindPredicate[Tag, T, K] =
    new IndexBindPredicate[Tag, T, K](self, "CONTAINS KEY", innerCodec)
}

/** Mixed into a [[Column]] by `Table.index` to record that an ENTRIES
  * index covering `col.entry` has been declared on a map column.
  * Overrides [[Column.entry]] to produce an [[IndexPredicate]] instead of
  * a plain [[Predicate]], exempting it from `allowFiltering` (see
  * [[PredicateShape]]).
  */
trait EntriesIndexed[T] { self: TableDef#Column[T] =>
  override def entry[K, V](key: K, value: V)(
      implicit ev: T <:< scala.collection.Map[K, V],
      keyCodec: TypeCodec[K],
      valueCodec: TypeCodec[V]
  ): IndexEntryPredicate[Tag, T, K, V] =
    new IndexEntryPredicate[Tag, T, K, V](self, key, value)

  override def entry[K, V](@unused k: BindMarker, @unused v: BindMarker)(
      implicit ev: T <:< scala.collection.Map[K, V],
      keyCodec: TypeCodec[K],
      valueCodec: TypeCodec[V],
      tupleCodec: TypeCodec[(K, V)]
  ): IndexEntryBindPredicate[Tag, T, K, V] =
    new IndexEntryBindPredicate[Tag, T, K, V](self)
}

/** Every index-backed exemption combined: `===`, `contains`, `containsKey`,
  * and `entry` are all exempt from `allowFiltering`. Mixed in by the
  * no-choice `Table.index(col)`, which grants everything applicable to
  * `col`'s type (see [[IndexTargets]]) — the same as it always has. For a
  * map column with only SOME of its physical indexes declared, use the
  * `on`-parameterized `Table.index` overloads instead, which mix in only
  * the matching one(s) of [[ValuesIndexed]] / [[KeysIndexed]] /
  * [[EntriesIndexed]].
  */
trait Indexed[T]
    extends EqIndexed[T]
    with ValuesIndexed[T]
    with KeysIndexed[T]
    with EntriesIndexed[T] { self: TableDef#Column[T] => }
