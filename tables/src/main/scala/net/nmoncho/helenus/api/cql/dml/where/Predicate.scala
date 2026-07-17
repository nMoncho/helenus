/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import scala.annotation.unused

import net.nmoncho.helenus.api.cql.TableDef
import shapeless.HList
import shapeless.ops.hlist.Prepend

sealed trait Predicate[T] extends WhereClause {
  def column: TableDef#Column[T]
  def operator: String

  def toCQL: String = s"${column.name} $operator ?"
}

/** A WHERE-clause predicate. */
sealed class ValuePredicate[T](val column: TableDef#Column[T], val operator: String, val value: T)
    extends Predicate[T] {
  override def toCQL: String    = s"${column.name} $operator ${column.codec.format(value)}"
  override def toString: String = s"Predicate($toCQL)"
}

object Predicate {
  def apply[T](column: TableDef#Column[T], operator: String, value: T): Predicate[T] =
    new ValuePredicate(column, operator, value)

  /** `and` on a single predicate, combining it with another predicate or an
    * existing [[Conjunction]] inside a `where(...)`. The type-level
    * contributions of both sides are merged, so the `execute` gate sees
    * through combined clauses. Defined in the `Predicate` companion so it is
    * in the implicit scope of every predicate subtype without imports.
    */
  implicit final class PredicateAndOps[
      P <: Predicate[_],
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
  */
final class EqPredicate[Col, T](column: TableDef#Column[T], value: T)
    extends ValuePredicate(column, "=", value)

/** A range (`<`, `>`, `<=`, `>=`) predicate tagged with the column's field tag
  * `Col`, tracked separately from equality constraints because CQL only
  * allows a range on the clustering column right after the `===` prefix.
  */
final class RangePredicate[Col, T](column: TableDef#Column[T], operator: String, value: T)
    extends ValuePredicate(column, operator, value)

/** An `IN` predicate tagged with the column's field tag `Col`, tracked in its
  * own set because CQL only allows IN on the last component of the primary
  * key, a position each gate checks according to its statement type.
  */
final class InPredicate[Col, T](val column: TableDef#Column[T], val values: Iterable[T])
    extends Predicate[T] {
  override val operator = "IN"

  override def toCQL: String =
    s"${column.name} $operator (${values.map(column.codec.format).mkString(", ")})"

  override def toString: String = s"Predicate($toCQL)"
}

// ---- bind variants (built with the `?` marker) ---------------------------
// Each mirrors its literal counterpart for the execute gates (same column-tag
// contribution) and additionally records the bound value type `T` in the
// statement's parameter list. Rendered as the native CQL bind marker `?`
// until filled by the function produced by `toFunction`.

/** Runtime side of a bind predicate: a predicate whose value is a hole,
  * fillable later with an argument of the captured column type.
  */
sealed abstract class BindPredicate[T](val column: TableDef#Column[T], val operator: String)
    extends Predicate[T] {

  /** Completes this predicate with a value (internally type-safe by construction). */
  private[cql] def fill(v: T): Predicate[T]
}

/** A bound equality: `col === ?`. */
final class EqBindPredicate[Col, T](column: TableDef#Column[T])
    extends BindPredicate[T](column, "=") {
  private[cql] def fill(v: T): Predicate[T] = Predicate(column, "=", v)
}

/** A bound range: `col > ?`, `col <= ?`, ... */
final class RangeBindPredicate[Col, T](
    column: TableDef#Column[T],
    operator: String
) extends BindPredicate[T](column, operator) {
  private[cql] def fill(v: T): Predicate[T] =
    Predicate(column, operator, v)
}

/** A bound multi-value equality: `col.in(?)`, binding a whole `Seq[T]`. */
final class InBindPredicate[Col, T](val column: TableDef#Column[T]) extends Predicate[T] {

  private[cql] def fill(v: Iterable[T]): Predicate[T] =
    new InPredicate(column, v)
  // Predicate(column, "IN", s"(${v.asInstanceOf[Seq[T]].map(ct.format).mkString(", ")})")

  override def operator: String = "IN"
}

/** A bound filtering-only predicate: `col !== ?`, `col.contains(?)`. */
final class FilterBindPredicate[T](column: TableDef#Column[T], operator: String)
    extends BindPredicate[T](column, operator) {
  private[cql] def fill(v: T): Predicate[T] =
    Predicate(column, operator, v)
}
