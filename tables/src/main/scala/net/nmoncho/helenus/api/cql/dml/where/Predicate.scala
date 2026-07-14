/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml.where

/** A WHERE-clause predicate. */
sealed class Predicate(val column: String, val operator: String, val value: String)
    extends WhereClause {
  def toCQL: String             = s"$column $operator $value"
  override def toString: String = s"Predicate($toCQL)"
}

object Predicate {
  def apply(column: String, operator: String, value: String): Predicate =
    new Predicate(column, operator, value)
}

/** An equality (`=`) predicate tagged with the column's field tag `Col` (the
  * literal type of the case-class field name, e.g. `"id"`).
  *
  * The phantom `Col` lets [[net.nmoncho.helenus.api.cql.dml.Select]] accumulate the set of
  * `===`-constrained columns at the type level. It is otherwise an ordinary
  * [[Predicate]].
  */
final class EqPredicate[Col](column: String, value: String) extends Predicate(column, "=", value)

/** A non-equality (`!=`) predicate tagged with the column's field tag `Col` (the
  * literal type of the case-class field name, e.g. `"id"`).
  *
  * The phantom `Col` lets [[net.nmoncho.helenus.api.cql.dml.Select]] accumulate the set of
  * `===`-constrained columns at the type level. It is otherwise an ordinary
  * [[Predicate]].
  */
final class NotEqPredicate[Col](column: String, value: String) extends Predicate(column, "=", value)

/** A range (`<`, `>`, `<=`, `>=`) predicate tagged with the column's field tag
  * `Col`, tracked separately from equality constraints because CQL only
  * allows a range on the clustering column right after the `===` prefix.
  */
final class RangePredicate[Col](column: String, operator: String, value: String)
    extends Predicate(column, operator, value)

/** An `IN` predicate tagged with the column's field tag `Col`, tracked in its
  * own set because CQL only allows IN on the last component of the primary
  * key, a position each gate checks according to its statement type.
  */
final class InPredicate[Col](column: String, values: String) extends Predicate(column, "IN", values)
