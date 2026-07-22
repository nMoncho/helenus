/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package ddl

import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.cql.dml.ContainsValue
import net.nmoncho.helenus.api.cql.dml.where.IndexEntryPredicate
import net.nmoncho.helenus.api.cql.dml.where.IndexEqPredicate
import net.nmoncho.helenus.api.cql.dml.where.IndexPredicate

/** Mixed into a [[Column]] by `Table.index` to record that a secondary
  * index has been declared on it. Overrides [[Column.contains]] and
  * [[Column.containsKey]] to produce an [[IndexPredicate]], and
  * [[Column.===]] to produce an [[IndexEqPredicate]], instead of the usual
  * predicate types: CQL can satisfy a `CONTAINS` / `CONTAINS KEY` / `=`
  * restriction directly through the index — on ANY column, not just
  * collections — so unlike the non-indexed case none of them force
  * `allowFiltering` (see [[PredicateShape]]).
  */
trait Indexed[T] { self: TableDef#Column[T] =>

  override def contains[V](
      value: V
  )(
      implicit @unused containsEv: ContainsValue[T, V],
      innerCodec: TypeCodec[V]
  ): IndexPredicate[Tag, T, V] =
    new IndexPredicate[Tag, T, V](self, "CONTAINS", value, innerCodec)

  override def containsKey[K, V](value: K)(
      implicit ev: T <:< scala.collection.Map[K, V],
      innerCodec: TypeCodec[K]
  ): IndexPredicate[Tag, T, K] =
    new IndexPredicate[Tag, T, K](self, "CONTAINS KEY", value, innerCodec)

  override def entry[K, V](key: K, value: V)(
      implicit ev: T <:< scala.collection.Map[K, V],
      keyCodec: TypeCodec[K],
      valueCodec: TypeCodec[V]
  ): IndexEntryPredicate[Tag, T, K, V] =
    new IndexEntryPredicate[Tag, T, K, V](self, key, value)

  override def ===(value: T): IndexEqPredicate[Tag, T] =
    new IndexEqPredicate[Tag, T](self, value)

}
