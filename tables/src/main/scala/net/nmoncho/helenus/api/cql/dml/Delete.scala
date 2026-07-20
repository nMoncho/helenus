/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql
package dml

import java.time.Duration
import java.time.temporal.ChronoUnit

import scala.annotation.unused

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.api.cql.dml.where._
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A typed DELETE builder.
  *
  * @tparam T      the singleton type of the table
  * @tparam Eq     type-level `HList` of the columns constrained so far with `===`
  * @tparam In     type-level `HList` of the columns constrained so far with `in`
  *                (never accepted by the DELETE gate in this DSL)
  * @tparam Rng    type-level `HList` of the columns constrained so far with a
  *                range operator (plus the `RequiresFiltering` marker)
  * @tparam Params type-level `HList` of the bound parameter types collected
  *                from `?` markers, in writing order
  * @tparam M      [[DeleteMode.Rows]] for whole-row deletes, switched to
  *                [[DeleteMode.Columns]] by [[column]]
  *
  * The WHERE clause is built exactly like SELECT's: a single [[where]] taking
  * one predicate or a conjunction (`where(UsersTable.id === x and UsersTable.username === "y")`).
  * [[execute]] is gated by [[CanDelete]], which enforces the CQL rules for
  * the current mode: row deletes need the full partition key plus a valid
  * clustering prefix (optionally ending in a range), column-level deletes
  * need the entire primary key with `===` only. `toCQL` stays ungated for
  * inspection. A statement with `?` markers becomes a function via
  * [[toFunction]], gated by the same rules.
  */
final case class Delete[
    T <: TableDef with Singleton,
    Eq <: HList,
    In <: HList,
    Rng <: HList,
    Params <: HList,
    M <: DeleteMode
](
    table: T,
    columnsToDrop: Seq[TableDef#Column[_]] = Seq.empty,
    predicates: Seq[Predicate[_, _]]       = Seq.empty,
    timestampMicros: Option[Duration]      = None,
    ifExistsFlag: Boolean                  = false
) {

  /** Delete only the specified column (rather than the entire row). Switches
    * the builder to [[DeleteMode.Columns]], whose `execute` requires the
    * whole primary key to be `===` constrained.
    */
  def column(col: table.Column[_]): Delete[T, Eq, In, Rng, Params, DeleteMode.Columns] =
    new Delete[T, Eq, In, Rng, Params, DeleteMode.Columns](
      table,
      columnsToDrop :+ col,
      predicates,
      timestampMicros,
      ifExistsFlag
    )

  /** Add the WHERE clause: a single predicate or several combined with `and`,
    * e.g. `where(UsersTable.id === x and UsersTable.username === "alice")`.
    * The clause's type-level contribution is merged into the constraint state
    * checked by [[execute]]; bound parameters (`?`) are appended to `Params`
    * in writing order.
    */
  def where[
      P <: WhereClause,
      E <: HList,
      I <: HList,
      R <: HList,
      Pm <: HList,
      E2 <: HList,
      I2 <: HList,
      R2 <: HList,
      P2 <: HList
  ](pred: P)(
      implicit ps: PredicateShape.Aux[P, E, I, R, Pm],
      @unused pe: Prepend.Aux[E, Eq, E2],
      @unused pi: Prepend.Aux[I, In, I2],
      @unused pr: Prepend.Aux[R, Rng, R2],
      @unused pp: Prepend.Aux[Params, Pm, P2]
  ): Delete[T, E2, I2, R2, P2, M] =
    new Delete[T, E2, I2, R2, P2, M](
      table,
      columnsToDrop,
      predicates ++ ps.predicates(pred),
      timestampMicros,
      ifExistsFlag
    )

  def usingTimestamp(micros: Duration): Delete[T, Eq, In, Rng, Params, M] =
    copy(timestampMicros = Some(micros))

  def ifExists: Delete[T, Eq, In, Rng, Params, M] = copy(ifExistsFlag = true)

  def toCQL(
      implicit @unused ev: CanDelete[M, table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundParams: Params =:= HNil
  ): String = render(prepared = false)

  private[cql] def render(prepared: Boolean = false): String = {
    val colStr   = if (columnsToDrop.isEmpty) "" else columnsToDrop.map(_.name).mkString(", ") + " "
    val usingStr = timestampMicros
      .map(ts => s" USING TIMESTAMP ${ts.dividedBy(Duration.of(1, ChronoUnit.MICROS))}")
      .getOrElse("")
    val whereStr =
      if (predicates.isEmpty) ""
      else if (prepared) s" WHERE ${predicates.map(_.forPreparedStatement).mkString(" AND ")}"
      else s" WHERE ${predicates.map(_.toCQL).mkString(" AND ")}"
    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""

    s"DELETE ${colStr}FROM ${table.fullTableName}$usingStr$whereStr$ifExistsStr"
  }

  /** Run the delete. Available only when the WHERE clause satisfies the CQL
    * rules for this delete's mode (see [[CanDelete]]) and no `?` marker is
    * unbound; otherwise this call does not compile. There is no
    * ALLOW FILTERING escape hatch for DELETE, and this DSL does not admit IN
    * predicates in DELETE.
    */
  def execute()(
      implicit session: CqlSession,
      @unused ev: CanDelete[M, table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundParams: Params =:= HNil
  ): ResultSet =
    executeStatement(
      render(prepared = true),
      Seq.empty,
      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      predicates.asInstanceOf[Seq[BoundPredicate[_, _]]]
    )

  /** Turn a delete containing `?` markers into a `FunctionN` taking one
    * argument per marker (typed as the bound column, in writing order) and
    * returning the rendered CQL. Gated by the same rules as [[execute]]:
    * bound key columns count toward the restriction exactly like literal
    * ones.
    */
  def toFunction[F](
      implicit session: CqlSession,
      @unused ev: CanDelete[M, table.PK, table.CK, Eq, In, Rng],
      fp: FnFromProduct.Aux[Params => ResultSet, F]
  ): F = {
    val pstmt = session.prepare(render(prepared = true))

    fp { params =>
      val values = Binding.values(params).iterator

      session.execute(bindPredicates(pstmt.bind(), predicates, values))
    }
  }

  override def toString: String = render()
}

object Delete {

  def apply[T <: TableDef with Singleton](
      table: T
  ): Delete[T, HNil, HNil, HNil, HNil, DeleteMode.Rows] =
    new Delete[T, HNil, HNil, HNil, HNil, DeleteMode.Rows](table)
}
