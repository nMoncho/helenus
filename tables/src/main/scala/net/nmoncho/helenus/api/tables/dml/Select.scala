/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.tables.dml.Select.orderedPredicates
import net.nmoncho.helenus.api.tables.dml.where._
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A typed SELECT builder.
  *
  * @tparam T      the singleton type of the table being queried
  * @tparam Eq     type-level `HList` of the columns constrained so far with `===`
  * @tparam In     type-level `HList` of the columns constrained so far with `in`
  * @tparam Rng    type-level `HList` of the columns constrained so far with a
  *                range operator (plus the `RequiresFiltering` marker for
  *                predicates that always need filtering, such as `contains`)
  * @tparam Params type-level `HList` of the bound parameter types collected
  *                from `?` markers, in writing order
  *
  * The WHERE clause is built with a single [[where]] call; several predicates
  * are combined with the `and` combinator on predicates themselves, in any
  * order: `where(UsersTable.id === x and UsersTable.username === "alice")`.
  * Rendering reorders predicates to CQL order (partition key, then clustering
  * columns, then the rest). The ungated [[execute]] is available only when
  * the accumulated constraints form a valid primary-key restriction (see
  * [[CanExecute]], including the IN placement rules), or when there are no
  * constraints at all; every other query must
  * go through [[allowFiltering]]`.execute`.
  */
final case class Select[
    T <: TableDef with Singleton,
    Eq <: HList,
    In <: HList,
    Rng <: HList,
    Params <: HList,
    Out
](
    table: T,
    columns: Seq[String],
    keyColumns: Seq[String],
    rowMapper: RowMapper[Out],
    predicates: Seq[Predicate[_, _]] = Seq.empty,
    limitValue: Option[Int]          = None,
    orderByClauses: Seq[ColumnOrder] = Seq.empty
) {

  /** Add the WHERE clause: a single predicate or several combined with `and`,
    * e.g. `where(UsersTable.id === x and UsersTable.username === "alice")`.
    * The clause's type-level contribution is computed by [[PredicateShape]]
    * and merged into the query state; bound parameters (`?`) are appended to
    * `Params` in writing order.
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
  ): Select[T, E2, I2, R2, P2, Out] =
    new Select[T, E2, I2, R2, P2, Out](
      table,
      columns,
      keyColumns,
      rowMapper,
      predicates ++ ps.predicates(pred),
      limitValue,
      orderByClauses
    )

  // TODO unify this with `toCQL`, no need to have one that can be used as escape hatch

  /** Run the query. Available only when the WHERE clause is a valid
    * primary-key restriction (full partition key by `===`, a contiguous
    * clustering `===` prefix, optionally ranges on the next clustering column,
    * IN only on the last partition-key or clustering column), or when there
    * is no WHERE clause at all, and no `?` marker is unbound. Otherwise this
    * call does not compile; use `allowFiltering.execute` or [[prepare]].
    */
  def execute()(
      implicit session: CqlSession,
      @unused ev: CanSelect[table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundParams: Params =:= HNil
  ): ResultSet = // TODO change this to `PagingIterable[Out]` when we can define `Out`
    executeStatement(
      Select.render(this, allowFiltering = false, prepared = true),
      Seq.empty,
      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      orderedPredicates(this).asInstanceOf[Seq[BoundPredicate[_, _]]]
    )

  def prepare[F](
      implicit @unused ev: CanSelect[table.PK, table.CK, Eq, In, Rng],
      session: CqlSession,
      to: ToPrepared[Params],
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
  ): to.Out#AsOut[Out] =
    to(Select.render(this, allowFiltering = false, prepared = true), Nil, orderedPredicates(this))
      .as(rowMapper)

  def prepareAsync[F](
      implicit @unused ev: CanSelect[table.PK, table.CK, Eq, In, Rng],
      session: Future[CqlSession],
      ec: ExecutionContext,
      to: ToPrepared[Params],
      fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
  ): Future[to.Out#AsOut[Out]] =
    session.map { implicit s =>
      to(Select.render(this, allowFiltering = false, prepared = true), Nil, orderedPredicates(this))
        .as(rowMapper)
    }

  private[dml] def withBoundValues(
      bstmt: BoundStatement,
      params: HList
  ): BoundStatement = {
    val values = Binding.values(params).iterator

    orderedPredicates(this).zipWithIndex.foldLeft(bstmt) {
      case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx, p.value)

      case (bstmt, (p: BindPredicate[_, Any], idx)) =>
        p.bind(bstmt, idx, values.next())
    }
  }

  /** Opt out of the primary-key requirement. The returned query can always be
    * executed, at the cost of a server-side `ALLOW FILTERING` scan.
    */
  def allowFiltering: Select.Filtering[T, Eq, In, Rng, Params, Out] = new Select.Filtering(this)

  def limit(n: Int): Select[T, Eq, In, Rng, Params, Out] = copy(limitValue = Some(n))

  /** Add ORDER BY clauses, built from a column's `asc` / `desc` methods, e.g.
    * `orderBy(username.desc)` or `orderBy(year.asc, ts.desc)`. A bare column
    * defaults to ascending via the other overload.
    */
  def orderBy(orders: ColumnOrder*): Select[T, Eq, In, Rng, Params, Out] =
    copy(orderByClauses = orderByClauses ++ orders)

  /** Order ascending by the given column (the CQL default direction). */
  def orderBy(col: table.Column[_]): Select[T, Eq, In, Rng, Params, Out] =
    orderBy(col.asc)

  def toCQL(
      implicit @unused ev: CanSelect[table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundParams: Params =:= HNil
  ): String = Select.render(this, allowFiltering = false)

  override def toString: String = Select.render(this, allowFiltering = false)
}

object Select {

  def apply[T <: TableDef with Singleton, Out](
      table: T,
      columns: Seq[TableDef#Column[_]],
      keyColumns: Seq[String]
  )(implicit rowMapper: RowMapper[Out]): Select[T, HNil, HNil, HNil, HNil, Out] =
    new Select[T, HNil, HNil, HNil, HNil, Out](table, columns.map(_.name), keyColumns, rowMapper)

  /** A SELECT that has opted into `ALLOW FILTERING`. Its [[execute]] and
    * [[prepare]] carry no primary-key requirement.
    */
  final class Filtering[
      T <: TableDef with Singleton,
      Eq <: HList,
      In <: HList,
      Rng <: HList,
      Params <: HList,
      Out
  ](
      private[tables] val select: Select[T, Eq, In, Rng, Params, Out]
  ) {

    def toCQL: String = render(select, allowFiltering = true)

    // TODO change this to `PagingIterable[Out]` when we can define `Out`
    def execute()(implicit session: CqlSession): ResultSet = {
      val pstmt = session.prepare(Select.render(select, allowFiltering = true, prepared = true))

      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      val bstmt = select.withBoundValues(pstmt.bind(), HNil)

      session.execute(bstmt)
    }

    /** Like `Select.prepare`, without the primary-key requirement. */
    def prepare[F](
        implicit session: CqlSession,
        to: ToPrepared[Params],
        fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
    ): to.Out#AsOut[Out] =
      to(
        Select.render(select, allowFiltering = true, prepared = true),
        Nil,
        orderedPredicates(select)
      )
        .as(select.rowMapper)

    def prepareAsync[F](
        implicit session: Future[CqlSession],
        ec: ExecutionContext,
        to: ToPrepared[Params],
        fp: FnFromProduct.Aux[Params => ScalaBoundStatement[Row], F]
    ): Future[to.Out#AsOut[Out]] =
      session.map { implicit s =>
        to(
          Select.render(select, allowFiltering = true, prepared = true),
          Nil,
          orderedPredicates(select)
        )
          .as(select.rowMapper)
      }
  }

  private[tables] def render[
      T <: TableDef with Singleton,
      Eq <: HList,
      In <: HList,
      Rng <: HList,
      Params <: HList,
      Out
  ](
      s: Select[T, Eq, In, Rng, Params, Out],
      allowFiltering: Boolean,
      prepared: Boolean = false
  ): String = {
    val colStr   = if (s.columns.isEmpty) "*" else s.columns.mkString(", ")
    val whereStr = renderPredicates(orderedPredicates(s), prepared)

    val orderStr =
      if (s.orderByClauses.isEmpty) ""
      else s" ORDER BY ${s.orderByClauses.map(_.toCQL).mkString(", ")}"

    val limitStr          = s.limitValue.map(l => s" LIMIT $l").getOrElse("")
    val allowFilteringStr = if (allowFiltering) " ALLOW FILTERING" else ""

    s"SELECT $colStr FROM ${s.table.fullTableName}$whereStr$orderStr$limitStr$allowFilteringStr"
  }

  /** Reorders predicates to CQL order regardless of how the user chained them:
    * partition-key columns first (in key order), then clustering columns (in
    * declaration order), then everything else in insertion order. The sort is
    * stable, so several predicates on the same column (a slice) keep their
    * relative order. Bound parameters are filled in writing order BEFORE this
    * reordering, so `?` positions and argument positions always agree.
    */
  private def orderedPredicates[
      T <: TableDef with Singleton,
      Eq <: HList,
      In <: HList,
      Rng <: HList,
      Params <: HList,
      Out
  ](
      s: Select[T, Eq, In, Rng, Params, Out]
  ): Seq[Predicate[_, _]] = {
    val keyIndex: Map[String, Int] = s.keyColumns.zipWithIndex.toMap
    s.predicates.sortBy(p => keyIndex.getOrElse(p.column.name, Int.MaxValue))
  }
}
