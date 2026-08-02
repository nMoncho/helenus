/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import java.time.Duration

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.tables.dml.where._
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.ops.function.FnFromProduct
import shapeless.ops.hlist.Prepend

/** A typed UPDATE builder.
  *
  * @tparam T       the singleton type of the table
  * @tparam Eq      type-level `HList` of the columns constrained so far with `===`
  * @tparam In      type-level `HList` of the columns constrained so far with `in`
  * @tparam Rng     type-level `HList` of the columns constrained so far with a
  *                 range operator (plus the `RequiresFiltering` marker)
  * @tparam SetPm   bound value types from `set(col := ?)`, in writing order
  * @tparam WherePm bound value types from `?` markers in the WHERE clause, in
  *                 writing order
  * @tparam Cols    type-level `HList` accumulating one element per SET
  *                 assignment added (via either `set` overload), in writing
  *                 order: the assignment's value type `V` for a literal
  *                 (`set(col := v)`), or [[BindMarker]] for a bound one
  *                 (`set(col := ?)`). Its only purpose is to make "at least one
  *                 SET assignment" a compile-time fact: it starts at `HNil` and
  *                 every `set(...)` prepends an element, so [[execute]] /
  *                 [[prepare]] / [[prepareAsync]] can require [[NonEmpty]]
  *                 evidence that has no instance for `HNil`.
  *
  *                 The WHERE clause is built exactly like SELECT's: a single [[where]] taking
  *                 one predicate or a conjunction. [[execute]] is gated by [[CanUpdate]],
  *                 which enforces the CQL rule that an UPDATE must identify rows by the
  *                 entire primary key with `===` (`in` allowed on the last component only).
  *                 `toCQL` stays ungated for inspection.
  *
  *                 A statement with `?` markers becomes a function via [[prepare]]; its
  *                 arguments follow the rendered statement order: SET parameters first, then
  *                 WHERE parameters.
  */
final case class Update[
    T <: TableDef with Singleton,
    Eq <: HList,
    In <: HList,
    Rng <: HList,
    SetPm <: HList,
    WherePm <: HList,
    Cols <: HList
](
    table: T,
    assignments: Seq[TableDef#Assignment[_]] = Seq.empty,
    predicates: Seq[Predicate[_, _]]         = Seq.empty,
    ttlSeconds: Option[Duration]             = None,
    timestampMicros: Option[Duration]        = None,
    ifExistsFlag: Boolean                    = false
) {

  def set[V](assignment: table.Assignment[V]): Update[T, Eq, In, Rng, SetPm, WherePm, V :: Cols] =
    new Update[T, Eq, In, Rng, SetPm, WherePm, V :: Cols](
      table,
      assignments :+ assignment,
      predicates,
      ttlSeconds,
      timestampMicros,
      ifExistsFlag
    )

  /** A bound assignment (`col := ?`), appended to the SET parameter list. */
  def set[V, P2 <: HList](
      assignment: table.BindAssignment[_, V]
  )(
      implicit @unused pp: Prepend.Aux[SetPm, V :: HNil, P2]
  ): Update[T, Eq, In, Rng, P2, WherePm, BindMarker :: Cols] =
    new Update[T, Eq, In, Rng, P2, WherePm, BindMarker :: Cols](
      table,
      assignments :+ assignment,
      predicates,
      ttlSeconds,
      timestampMicros,
      ifExistsFlag
    )

  /** Add the WHERE clause: a single predicate or several combined with `and`,
    * e.g. `where(UsersTable.id === x and UsersTable.username === "alice")`.
    * The clause's type-level contribution is merged into the constraint state
    * checked by [[execute]]; bound parameters (`?`) are appended to the WHERE
    * parameter list.
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
      W2 <: HList
  ](pred: P)(
      implicit ps: PredicateShape.Aux[P, E, I, R, Pm],
      @unused pe: Prepend.Aux[E, Eq, E2],
      @unused pi: Prepend.Aux[I, In, I2],
      @unused pr: Prepend.Aux[R, Rng, R2],
      @unused pw: Prepend.Aux[WherePm, Pm, W2]
  ): Update[T, E2, I2, R2, SetPm, W2, Cols] =
    new Update[T, E2, I2, R2, SetPm, W2, Cols](
      table,
      assignments,
      predicates ++ ps.predicates(pred),
      ttlSeconds,
      timestampMicros,
      ifExistsFlag
    )

  def usingTTL(seconds: Duration): Update[T, Eq, In, Rng, SetPm, WherePm, Cols] =
    copy(ttlSeconds = Some(seconds))
  def usingTimestamp(micros: Duration): Update[T, Eq, In, Rng, SetPm, WherePm, Cols] =
    copy(timestampMicros = Some(micros))

  def ifExists: Update[T, Eq, In, Rng, SetPm, WherePm, Cols] = copy(ifExistsFlag = true)

  private[tables] def render(prepared: Boolean = false): String = {
    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""
    val whereStr    = renderPredicates(predicates, prepared)
    val usingStr    = renderUsing(ttlSeconds, timestampMicros)
    val setStr      = assignments
      .map {
        case simple: TableDef#BoundAssignment[_, _] if !prepared =>
          s"${simple.column.name} = ${simple.column.codec.format(simple.value)}"

        case assignment =>
          s"${assignment.column.name} = ?"
      }
      .mkString(", ")

    s"UPDATE ${table.fullTableName}$usingStr SET $setStr$whereStr$ifExistsStr"
  }

  def toCQL(
      implicit @unused ev: CanUpdate[table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundSet: SetPm =:= HNil,
      @unused noUnboundWhere: WherePm =:= HNil
  ): String = render()

  /** Run the update. Available only when the WHERE clause constrains the
    * entire primary key (see [[CanUpdate]]) and no `?` marker is unbound;
    * otherwise this call does not compile.
    */
  def execute()(
      implicit session: CqlSession,
      @unused ev: CanUpdate[table.PK, table.CK, Eq, In, Rng],
      @unused noUnboundSet: SetPm =:= HNil,
      @unused noUnboundWhere: WherePm =:= HNil,
      @unused nonEmpty: NonEmpty[Cols]
  ): ResultSet =
    executeStatement(
      render(prepared = true),
      // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
      assignments.asInstanceOf[Seq[TableDef#BoundAssignment[_, Any]]],
      // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
      predicates.asInstanceOf[Seq[BoundPredicate[_, _]]]
    )

  /** Turn an update containing `?` markers into a `FunctionN` returning the
    * rendered CQL. Arguments follow the rendered statement order: SET
    * parameters first, then WHERE parameters. Gated by the same rules as
    * [[execute]]: bound key columns count toward the full-primary-key
    * requirement exactly like literal ones.
    */
  def prepare[AllPm <: HList, F](
      implicit session: CqlSession,
      @unused ev: CanUpdate[table.PK, table.CK, Eq, In, Rng],
      @unused all: Prepend.Aux[SetPm, WherePm, AllPm],
      to: ToPrepared[AllPm],
      fp: FnFromProduct.Aux[AllPm => ScalaBoundStatement[Row], F],
      @unused nonEmpty: NonEmpty[Cols]
  ): to.Out =
    to(render(prepared = true), assignments, predicates)

  def prepareAsync[AllPm <: HList, F](
      implicit session: Future[CqlSession],
      ec: ExecutionContext,
      @unused ev: CanUpdate[table.PK, table.CK, Eq, In, Rng],
      @unused all: Prepend.Aux[SetPm, WherePm, AllPm],
      to: ToPrepared[AllPm],
      fp: FnFromProduct.Aux[AllPm => ScalaBoundStatement[Row], F],
      @unused nonEmpty: NonEmpty[Cols]
  ): Future[to.Out] =
    session.map { implicit s =>
      to(render(prepared = true), assignments, predicates)
    }

  override def toString: String = render()
}

object Update {

  def apply[T <: TableDef with Singleton](table: T): Update[T, HNil, HNil, HNil, HNil, HNil, HNil] =
    new Update[T, HNil, HNil, HNil, HNil, HNil, HNil](table)
}
