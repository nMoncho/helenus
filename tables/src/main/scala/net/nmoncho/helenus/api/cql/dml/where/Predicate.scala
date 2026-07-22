/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.BoundStatement
import net.nmoncho.helenus.api.cql.TableDef
import shapeless.HList
import shapeless.ops.hlist.Prepend

sealed trait Predicate[T, V] extends WhereClause {
  def column: TableDef#Column[T]
  def operator: String

  def toCQL: String                = s"${column.name} $operator ?"
  def forPreparedStatement: String = s"${column.name} $operator ?"

  // DO NOT put a default implementation! We need to know if this is unset
  def bind(bstmt: BoundStatement, idx: Int, value: V): BoundStatement

  override def toString: String = s"Predicate($toCQL)"
}

// Marker trait for predicates that have a value
sealed trait BoundPredicate[T, V] extends Predicate[T, V] {
  def value: V
}

/** A WHERE-clause predicate. */
sealed class SingleValuePredicate[T](
    val column: TableDef#Column[T],
    val operator: String,
    val value: T
) extends BoundPredicate[T, T] {
  override def toCQL: String =
    s"${column.name} $operator ${column.codec.format(value)}"

  override def bind(bstmt: BoundStatement, idx: Int, @unused _value: T): BoundStatement =
    bstmt.set[T](idx, value, column.codec)
}

// FIXME Maybe we don't need this, and we can just put this implementation on `InPredicate`
sealed class MultiValuePredicate[T, V <: Iterable[T]](
    val column: TableDef#Column[T],
    val operator: String,
    val value: V,
    val codec: TypeCodec[V]
) extends BoundPredicate[T, V] {
  override def toCQL: String =
    s"${column.name} $operator (${value.map(column.codec.format).mkString(", ")})"

  override def bind(bstmt: BoundStatement, idx: Int, @unused _value: V): BoundStatement =
    bstmt.set[V](idx, this.value, codec)
}

final class SingleValueOnCollectionPredicate[T, V](
    val column: TableDef#Column[T],
    val operator: String,
    val value: V,
    val codec: TypeCodec[V]
) extends BoundPredicate[T, V] {
  override def toCQL: String =
    s"${column.name} $operator ${codec.format(value)}"

  override def bind(bstmt: BoundStatement, idx: Int, @unused _value: V): BoundStatement =
    bstmt.set[V](idx, this.value, codec)
}

sealed class EntryPredicate[Col, T, K, V](
    val column: TableDef#Column[T],
    val key: K,
    val value: V
)(implicit keyCodec: TypeCodec[K], valueCodec: TypeCodec[V])
    extends BoundPredicate[T, V] {

  override val operator: String = "="

  override def toCQL: String                = s"${column.name}[${keyCodec.format(key)}] $operator ?"
  override def forPreparedStatement: String = s"${column.name}[${keyCodec.format(key)}] $operator ?"

  override def bind(bstmt: BoundStatement, idx: Int, @unused _value: V): BoundStatement =
    bstmt.set[V](idx, this.value, valueCodec)
}

object Predicate {
  def apply[T](column: TableDef#Column[T], operator: String, value: T): Predicate[T, T] =
    new SingleValuePredicate(column, operator, value)

  /** `and` on a single predicate, combining it with another predicate or an
    * existing [[Conjunction]] inside a `where(...)`. The type-level
    * contributions of both sides are merged, so the `execute` gate sees
    * through combined clauses. Defined in the `Predicate` companion so it is
    * in the implicit scope of every predicate subtype without imports.
    */
  implicit final class PredicateAndOps[
      P <: Predicate[_, _],
      E <: HList,
      I <: HList,
      R <: HList,
      Pm <: HList
  ](self: P)(
      implicit ps: PredicateShape.Aux[P, E, I, R, Pm]
  ) {
    def and[
        P2,
        E2 <: HList,
        I2 <: HList,
        R2 <: HList,
        Pm2 <: HList,
        EO <: HList,
        IO <: HList,
        RO <: HList,
        PO <: HList
    ](other: P2)(
        implicit ps2: PredicateShape.Aux[P2, E2, I2, R2, Pm2],
        @unused pe: Prepend.Aux[E, E2, EO],
        @unused pi: Prepend.Aux[I, I2, IO],
        @unused pr: Prepend.Aux[R, R2, RO],
        @unused pp: Prepend.Aux[Pm, Pm2, PO]
    ): Conjunction[EO, IO, RO, PO] =
      new Conjunction(ps.predicates(self) ++ ps2.predicates(other))
  }
}

/** An equality (`=`) predicate tagged with the column's field tag `Col` (the
  * literal type of the case-class field name, e.g. `"id"`).
  *
  * The phantom `Col` lets [[net.nmoncho.helenus.api.cql.dml.Select]] accumulate the set of
  * `===`-constrained columns at the type level. It is otherwise an ordinary
  * [[Predicate]].
  *
  * Not `final`: [[IndexEqPredicate]] extends it so `Column.===`'s declared
  * return type (needed to keep ordinary primary-key tracking working) can
  * still be overridden, covariantly, for an indexed column.
  */
sealed class EqPredicate[Col, T](column: TableDef#Column[T], value: T)
    extends SingleValuePredicate(column, "=", value)

/** A range (`<`, `>`, `<=`, `>=`) predicate tagged with the column's field tag
  * `Col`, tracked separately from equality constraints because CQL only
  * allows a range on the clustering column right after the `===` prefix.
  */
final class RangePredicate[Col, T](column: TableDef#Column[T], operator: String, value: T)
    extends SingleValuePredicate(column, operator, value)

/** An `IN` predicate tagged with the column's field tag `Col`, tracked in its
  * own set because CQL only allows IN on the last component of the primary
  * key, a position each gate checks according to its statement type.
  */
final class InPredicate[Col, T, V <: Iterable[T]](
    column: TableDef#Column[T],
    values: V,
    codec: TypeCodec[V]
) extends MultiValuePredicate[T, V](column, "IN", values, codec)

/** A `CONTAINS` / `CONTAINS KEY` predicate on a column with a declared
  * secondary index (see `Table.index` / [[TableDef.Indexed]]). Unlike the
  * plain [[Predicate]] produced by `contains` / `containsKey` on a
  * non-indexed column, CQL can satisfy this directly through the index, so it
  * does not require `ALLOW FILTERING` (see [[PredicateShape]]).
  */
final class IndexPredicate[Col, T, V](
    val column: TableDef#Column[T],
    val operator: String,
    val value: V,
    val codec: TypeCodec[V]
) extends BoundPredicate[T, V] {
  override def toCQL: String =
    s"${column.name} $operator ${codec.format(value)}"

  override def bind(bstmt: BoundStatement, idx: Int, @unused _value: V): BoundStatement =
    bstmt.set[V](idx, this.value, codec)
}

/** An equality (`=`) predicate on a column with a declared secondary index
  * (see `Table.index` / [[TableDef.Indexed]]). Unlike the plain
  * [[EqPredicate]] produced by `===` on a non-indexed column (which needs
  * either full primary-key membership or `ALLOW FILTERING` to be
  * gate-admitted), CQL can satisfy this directly through the index, on any
  * column type, so it does not require `ALLOW FILTERING` (see [[PredicateShape]]).
  */
final class IndexEqPredicate[Col, T](column: TableDef#Column[T], value: T)
    extends EqPredicate[Col, T](column, value)

final class IndexEntryPredicate[Col, T, K, V](
    column: TableDef#Column[T],
    key: K,
    value: V
)(implicit keyCodec: TypeCodec[K], valueCodec: TypeCodec[V])
    extends EntryPredicate[Col, T, K, V](column, key, value)

// ---- bind variants (built with the `?` marker) ---------------------------
// Each mirrors its literal counterpart for the execute gates (same column-tag
// contribution) and additionally records the bound value type `T` in the
// statement's parameter list. Rendered as the native CQL bind marker `?`
// until filled by the function produced by `toFunction`.

/** Runtime side of a bind predicate: a predicate whose value is a hole,
  * fillable later with an argument of the captured column type.
  */
sealed abstract class BindPredicate[T, V](val column: TableDef#Column[T], val operator: String)
    extends Predicate[T, V] {

  /** Completes this predicate with a value (internally type-safe by construction). */
  private[cql] def fill(v: V): Predicate[T, V]
}

object BindPredicate {
  def apply[T](column: TableDef#Column[T], operator: String): BindPredicate[T, T] =
    new SingleValueBindPredicate(column, operator)
}

class SingleValueBindPredicate[T](column: TableDef#Column[T], operator: String)
    extends BindPredicate[T, T](column, operator) {

  override def bind(bstmt: BoundStatement, idx: Int, value: T): BoundStatement =
    bstmt.set[T](idx, value, column.codec)

  private[cql] def fill(v: T): Predicate[T, T] = Predicate(column, operator, v)
}

/** A bound equality: `col === ?`. */
final class EqBindPredicate[Col, T](column: TableDef#Column[T])
    extends SingleValueBindPredicate[T](column, "=")

/** A bound range: `col > ?`, `col <= ?`, ... */
final class RangeBindPredicate[Col, T](
    column: TableDef#Column[T],
    operator: String
) extends SingleValueBindPredicate[T](column, operator)

/** A bound multi-value equality: `col.in(?)`, binding a whole `Seq[T]`. */
final class InBindPredicate[Col, T, V <: Iterable[T]](
    column: TableDef#Column[T],
    codec: TypeCodec[V]
) extends BindPredicate[T, V](column, "IN") {

  private[cql] def fill(v: V): Predicate[T, V] =
    new InPredicate(column, v, codec)

  override def bind(bstmt: BoundStatement, idx: Int, values: V): BoundStatement =
    bstmt.set[V](idx, values, codec)
}
