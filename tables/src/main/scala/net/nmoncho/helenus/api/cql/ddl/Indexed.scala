/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package ddl

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
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
  )(implicit ev: T <:< Iterable[V], innerType: TypeCodec[V]): IndexPredicate[Tag, T, V] =
    new IndexPredicate[Tag, T, V](self, "CONTAINS", value, innerType)

  override def containsKey[K, V](value: K)(
      implicit ev: T <:< scala.collection.Map[K, V],
      innerType: TypeCodec[K]
  ): IndexPredicate[Tag, T, K] =
    new IndexPredicate[Tag, T, K](self, "CONTAINS KEY", value, innerType)

  override def ===(value: T): IndexEqPredicate[Tag, T] =
    new IndexEqPredicate[Tag, T](self, value)

}
