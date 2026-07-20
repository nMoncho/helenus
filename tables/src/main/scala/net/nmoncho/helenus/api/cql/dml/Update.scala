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
  *
  * The WHERE clause is built exactly like SELECT's: a single [[where]] taking
  * one predicate or a conjunction. [[execute]] is gated by [[CanUpdate]],
  * which enforces the CQL rule that an UPDATE must identify rows by the
  * entire primary key with `===` (`in` allowed on the last component only).
  * `toCQL` stays ungated for inspection.
  *
  * A statement with `?` markers becomes a function via [[toFunction]]; its
  * arguments follow the rendered statement order: SET parameters first, then
  * WHERE parameters.
  */
final case class Update[
    T <: TableDef with Singleton,
    Eq <: HList,
    In <: HList,
    Rng <: HList,
    SetPm <: HList,
    WherePm <: HList
](
    table: T,
    assignments: Seq[TableDef#Assignment[_]] = Seq.empty,
    predicates: Seq[Predicate[_, _]]         = Seq.empty,
    ttlSeconds: Option[Duration]             = None,
    timestampMicros: Option[Duration]        = None,
    ifExistsFlag: Boolean                    = false
) {

  def set(assignment: table.Assignment[_]): Update[T, Eq, In, Rng, SetPm, WherePm] =
    copy(assignments = assignments :+ assignment)

  /** A bound assignment (`col := ?`), appended to the SET parameter list. */
  def set[V, P2 <: HList](
      assignment: table.BindAssignment[V]
  )(implicit @unused pp: Prepend.Aux[SetPm, V :: HNil, P2]): Update[T, Eq, In, Rng, P2, WherePm] =
    new Update[T, Eq, In, Rng, P2, WherePm](
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
  ): Update[T, E2, I2, R2, SetPm, W2] =
    new Update[T, E2, I2, R2, SetPm, W2](
      table,
      assignments,
      predicates ++ ps.predicates(pred),
      ttlSeconds,
      timestampMicros,
      ifExistsFlag
    )

  def usingTTL(seconds: Duration): Update[T, Eq, In, Rng, SetPm, WherePm] =
    copy(ttlSeconds = Some(seconds))
  def usingTimestamp(micros: Duration): Update[T, Eq, In, Rng, SetPm, WherePm] =
    copy(timestampMicros = Some(micros))

  def ifExists: Update[T, Eq, In, Rng, SetPm, WherePm] = copy(ifExistsFlag = true)

  private[cql] def render(prepared: Boolean = false): String = {
    // TODO just like Insert, add this requirement at type-level
    require(assignments.nonEmpty, "UPDATE must have at least one SET assignment")

    val usingParts = Seq(
      ttlSeconds.map(t => s"TTL ${t.toSeconds}"),
      timestampMicros.map(ts => s"TIMESTAMP ${ts.dividedBy(Duration.of(1, ChronoUnit.MICROS))}")
    ).flatten

    val usingStr = if (usingParts.isEmpty) "" else s" USING ${usingParts.mkString(" AND ")}"
    val setStr   = assignments
      .map {
        case simple: TableDef#SimpleAssignment[_] if !prepared =>
          s"${simple.column.name} = ${simple.column.codec.format(simple.value)}"

        case assignment =>
          s"${assignment.column.name} = ?"
      }
      .mkString(", ")

    val whereStr =
      if (predicates.isEmpty) ""
      else if (prepared) s" WHERE ${predicates.map(_.forPreparedStatement).mkString(" AND ")}"
      else s" WHERE ${predicates.map(_.toCQL).mkString(" AND ")}"

    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""

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
      @unused noUnboundWhere: WherePm =:= HNil
  ): ResultSet = {
    val pstmt           = session.prepare(render(prepared = true))
    val assignmentCount = assignments.length

    // Safe to case this to `Seq[SimpleAssignment[_]]` as there are no unbound parameters
    val bstmt = assignments
      .asInstanceOf[Seq[TableDef#SimpleAssignment[Any]]]
      .zipWithIndex
      .foldLeft(pstmt.bind()) { case (bstmt, (as, idx)) =>
        bstmt.set(idx, as.value, as.column.codec)
      }

    // Safe to case this to `Seq[BoundPredicate[_, _]]` as there are no unbound parameters
    val withPredicates = predicates
      .asInstanceOf[Seq[BoundPredicate[_, _]]]
      .zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + assignmentCount, p.value)
      }

    session.execute(withPredicates)
  }

  /** Turn an update containing `?` markers into a `FunctionN` returning the
    * rendered CQL. Arguments follow the rendered statement order: SET
    * parameters first, then WHERE parameters. Gated by the same rules as
    * [[execute]]: bound key columns count toward the full-primary-key
    * requirement exactly like literal ones.
    */
  def toFunction[AllPm <: HList, F](
      implicit session: CqlSession,
      @unused ev: CanUpdate[table.PK, table.CK, Eq, In, Rng],
      @unused all: Prepend.Aux[SetPm, WherePm, AllPm],
      fp: FnFromProduct.Aux[AllPm => ResultSet, F]
  ): F = {
    val pstmt = session.prepare(render(prepared = true))

    fp { params =>
      val values          = Binding.values(params).iterator
      val assignmentCount = assignments.length

      val bstmt = assignments.zipWithIndex
        .foldLeft(pstmt.bind()) {
          case (bstmt, (as: TableDef#BindAssignment[Any], idx)) =>
            bstmt.set(idx, values.next(), as.column.codec)

          case (bstmt, (as: TableDef#SimpleAssignment[Any], idx)) =>
            bstmt.set(idx, as.value, as.column.codec)
        }

      val withPredicates = predicates.zipWithIndex.foldLeft(bstmt) {
        case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
          p.bind(bstmt, idx + assignmentCount, p.value)

        case (bstmt, (p: BindPredicate[_, Any], idx)) =>
          p.bind(bstmt, idx + assignmentCount, values.next())
      }

      session.execute(withPredicates)
    }
  }

  override def toString: String = render()
}

object Update {

  def apply[T <: TableDef with Singleton](table: T): Update[T, HNil, HNil, HNil, HNil, HNil] =
    new Update[T, HNil, HNil, HNil, HNil, HNil](table)
}
