/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.cql.TableDef
import shapeless.HList
import shapeless.ops.hlist.Prepend

/** A WHERE-clause predicate. */
sealed class Predicate(val column: String, val operator: String, val value: String)
    extends WhereClause {
  def toCQL: String             = s"$column $operator $value"
  override def toString: String = s"Predicate($toCQL)"
}

object Predicate {
  def apply(column: String, operator: String, value: String): Predicate =
    new Predicate(column, operator, value)

  /** `and` on a single predicate, combining it with another predicate or an
    * existing [[Conjunction]] inside a `where(...)`. The type-level
    * contributions of both sides are merged, so the `execute` gate sees
    * through combined clauses. Defined in the `Predicate` companion so it is
    * in the implicit scope of every predicate subtype without imports.
    */
  implicit final class PredicateAndOps[
      P <: Predicate,
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
final class EqPredicate[Col](column: TableDef#Column[_], value: String)
    extends Predicate(column.name, "=", value)

/** A non-equality (`!=`) predicate tagged with the column's field tag `Col` (the
  * literal type of the case-class field name, e.g. `"id"`).
  *
  * The phantom `Col` lets [[net.nmoncho.helenus.api.cql.dml.Select]] accumulate the set of
  * `===`-constrained columns at the type level. It is otherwise an ordinary
  * [[Predicate]].
  */
final class NotEqPredicate[Col](column: TableDef#Column[_], value: String)
    extends Predicate(column.name, "=", value)

/** A range (`<`, `>`, `<=`, `>=`) predicate tagged with the column's field tag
  * `Col`, tracked separately from equality constraints because CQL only
  * allows a range on the clustering column right after the `===` prefix.
  */
final class RangePredicate[Col](column: TableDef#Column[_], operator: String, value: String)
    extends Predicate(column.name, operator, value)

/** An `IN` predicate tagged with the column's field tag `Col`, tracked in its
  * own set because CQL only allows IN on the last component of the primary
  * key, a position each gate checks according to its statement type.
  */
final class InPredicate[Col](column: TableDef#Column[_], values: String)
    extends Predicate(column.name, "IN", values)

// ---- bind variants (built with the `?` marker) ---------------------------
// Each mirrors its literal counterpart for the execute gates (same column-tag
// contribution) and additionally records the bound value type `T` in the
// statement's parameter list. Rendered as the native CQL bind marker `?`
// until filled by the function produced by `toFunction`.

/** Runtime side of a bind predicate: a predicate whose value is a hole,
  * fillable later with an argument of the captured column type.
  */
sealed trait BindHole { self: Predicate =>

  /** Completes this predicate with a value (internally type-safe by construction). */
  private[cql] def fill(v: Any): Predicate
}

/** A bound equality: `col === ?`. */
final class EqBindPredicate[Col, T](column: TableDef#Column[_], ct: TypeCodec[T])
    extends Predicate(column.name, "=", "?")
    with BindHole {
  private[cql] def fill(v: Any): Predicate =
    Predicate(column.name, "=", ct.format(v.asInstanceOf[T]))

}

/** A bound range: `col > ?`, `col <= ?`, ... */
final class RangeBindPredicate[Col, T](
    column: TableDef#Column[_],
    operator: String,
    ct: TypeCodec[T]
) extends Predicate(column.name, operator, "?")
    with BindHole {
  private[cql] def fill(v: Any): Predicate =
    Predicate(column.name, operator, ct.format(v.asInstanceOf[T]))
}

/** A bound multi-value equality: `col.in(?)`, binding a whole `Seq[T]`. */
final class InBindPredicate[Col, T](column: TableDef#Column[_], ct: TypeCodec[T])
    extends Predicate(column.name, "IN", "?")
    with BindHole {
  private[cql] def fill(v: Any): Predicate =
    Predicate(column.name, "IN", s"(${v.asInstanceOf[Seq[T]].map(ct.format).mkString(", ")})")
}

/** A bound filtering-only predicate: `col !== ?`, `col.contains(?)`. */
final class FilterBindPredicate[T](column: TableDef#Column[_], operator: String, ct: TypeCodec[T])
    extends Predicate(column.name, operator, "?")
    with BindHole {
  private[cql] def fill(v: Any): Predicate =
    Predicate(column.name, operator, ct.format(v.asInstanceOf[T]))
}
