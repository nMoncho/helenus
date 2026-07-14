/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import scala.annotation.implicitNotFound
import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.cql.ddl.CreateTable
import net.nmoncho.helenus.api.cql.ddl.DropTable
import net.nmoncho.helenus.api.cql.dml.Select
import net.nmoncho.helenus.api.cql.dml.where._
import shapeless.::
import shapeless.Generic
import shapeless.HList
import shapeless.HNil

/** Base of every table definition: holds the inner column / assignment
  * classes, the type-level key declarations, and the entry points that do not
  * depend on the mapped case class. Users never extend this directly; they
  * extend [[Table]] with their case class.
  */
sealed abstract class TableDef(val keyspace: String, val tableName: String) {

  /** Type-level partition key: an `HList` of the partition-key columns' field
    * tags (e.g. `id.Tag :: HNil`), in key order. Drives both the compile-time
    * `execute` gates and the runtime key metadata (via [[ColumnNames]]).
    */
  type PK <: HList

  /** Type-level clustering columns, in declaration order (e.g.
    * `username.Tag :: HNil`, or `HNil` when the table has none). Wrap a tag
    * in `.Desc` / `.Asc` to declare the clustering order.
    */
  type CK <: HList

  def fullTableName: String = s"$keyspace.$tableName"

  // ---- entry points that do not need the mapped case class ----------------

  def drop: DropTable = DropTable(this)

  /** A typed column of this table. Instances are obtained with
    * `column("fieldName")`, which checks the field against the mapped case
    * class; `fieldName` is the case-class field, `name` the CQL column name
    * produced by the table's [[ColumnNamingScheme]].
    */
  class Column[T](val fieldName: String, val name: String)(implicit codec: TypeCodec[T]) {

    /** Type-level identity of this column: the literal type of the case-class
      * field name (e.g. `Tag = "id"`). Used in `PK` / `CK` declarations and
      * carried by predicates for the execute gates.
      */
    type Tag

    // ---- clustering sort direction (for use inside `type CK`) ------------

    /** This column sorted ascending, e.g. `type CK = ts.Asc :: HNil` (same as a bare `ts.Tag`). */
    final type Asc = net.nmoncho.helenus.api.cql.Asc[Tag]

    /** This column sorted descending, e.g. `type CK = ts.Desc :: HNil`. */
    final type Desc = net.nmoncho.helenus.api.cql.Desc[Tag]

    // ---- query-time sort direction (for use in `Select.orderBy`) ---------

    /** Ascending ORDER BY clause for this column, e.g. `select().orderBy(ts.asc)`. */
    def asc: ColumnOrder = ColumnOrder(name, descending = false)

    /** Descending ORDER BY clause for this column, e.g. `select().orderBy(ts.desc)`. */
    def desc: ColumnOrder = ColumnOrder(name, descending = true)

    // ---- predicates -------------------------------------------------------

    /** Equality predicate. Carries this column's field tag so the query
      * builder can track, at compile time, which columns are constrained by
      * equality.
      */
    def ===(value: T): EqPredicate[Tag] =
      new EqPredicate[Tag](name, codec.format(value))

    /** Range predicates. They also carry the column's field tag: a range is
      * allowed without ALLOW FILTERING only on the clustering column that
      * immediately follows the `===`-constrained prefix.
      */
    def >(value: T): RangePredicate[Tag] =
      new RangePredicate[Tag](name, ">", codec.format(value))
    def <(value: T): RangePredicate[Tag] =
      new RangePredicate[Tag](name, "<", codec.format(value))
    def >=(value: T): RangePredicate[Tag] =
      new RangePredicate[Tag](name, ">=", codec.format(value))
    def <=(value: T): RangePredicate[Tag] =
      new RangePredicate[Tag](name, "<=", codec.format(value))

    /** Never valid on a primary-key restriction: always requires ALLOW FILTERING. */
    def !==(value: T): Predicate = Predicate(name, "!=", codec.format(value))

    /** Multi-value equality. Carries the column's field tag: CQL allows IN
      * only on the last component of the primary key (the gates check the
      * position per statement type).
      */
    def in(values: Seq[T]): InPredicate[Tag] =
      new InPredicate[Tag](name, s"(${values.map(codec.format).mkString(", ")})")

    // TODO add evidence that this column is a collection
    def contains(value: T): Predicate =
      Predicate(name, "CONTAINS", codec.format(value))

    // TODO add evidence that this column is a collection
    def containsKye(value: T): Predicate =
      Predicate(name, "CONTAINS KEY", codec.format(value))

    override def toString: String =
      s"Column($fieldName -> $name ${codec.getCqlType.asCql(false, false)})"

    def toCQL: String = s"$name ${codec.getCqlType.asCql(false, false)}"
  }

}

abstract class Table[A](keyspace0: String, tableName0: String)(implicit columnsForA: ColumnsFor[A])
    extends TableDef(keyspace0, tableName0) {

  /** How case-class field names map to CQL column names. */
  protected def naming: ColumnNamingScheme = DefaultColumnNamingScheme

  /** Every table must register its columns; implement as
    * `protected val columns = registerAllColumns(id :: ... :: HNil)`.
    *
    * This is the completeness half of the case-class contract: adding a field
    * to `A` without declaring (and registering) a column val for it fails to
    * compile here, because the registered list no longer matches `A`'s
    * fields. The reference half is checked per val by [[column]].
    */
  protected def columns: Table.AllColumns

  /** Checks that `cols` lists a column for EVERY field of `A`, in field
    * declaration order, with matching value types. Computed columns are not
    * fields and must not be listed.
    */
  protected def registerAllColumns[L <: HList, R <: HList](
      @unused cols: L
  )(implicit @unused gen: Generic.Aux[A, R], @unused covers: CoversFields[L, R]): Table.AllColumns =
    new Table.AllColumns

  /** Witnesses that `L` is a list of this table's columns whose value types
    * are exactly `R` (the field types of `A`), in order.
    */
  @implicitNotFound(
    "The registered columns ${L} do not cover the fields of the case class " +
      "(expected value types ${R}, in field declaration order). " +
      "Declare a column val for every field and list them all in registerAllColumns."
  )
  protected sealed trait CoversFields[L <: HList, R <: HList]

  protected object CoversFields {

    implicit val nil: CoversFields[HNil, HNil] = new CoversFields[HNil, HNil] {}

    implicit def cons[H, C <: Column[H], LT <: HList, RT <: HList](
        implicit rest: CoversFields[LT, RT]
    ): CoversFields[C :: LT, H :: RT] = new CoversFields[C :: LT, H :: RT] {}
  }

  /** Reference a field of `A` as a column, stating its type explicitly:
    * `column[UUID]("id")`. The compiler verifies (via [[FieldOfType]]) that
    * the field exists in `A` and that its type is exactly `V`, so table and
    * case class can never drift.
    *
    * `V` is deliberately an explicit type ARGUMENT rather than inferred: the
    * val's type then does not depend on implicit resolution, which keeps IDEs
    * with weaker implicit search (IntelliJ) inferring `Column[V]` correctly.
    * (The compiler can also infer `V` if omitted, but IntelliJ cannot.)
    * Do NOT ascribe the val itself (`val id: Column[UUID] = ...`): the
    * ascription widens away the `Tag` refinement and genuinely breaks
    * `PK` / `CK` derivation.
    */
  protected def column[V: TypeCodec](name0: String with Singleton)(
      implicit @unused field: FieldOfType[A, name0.type, V]
  ): Column[V] { type Tag = name0.type } =
    new Column[V](name0, naming.map(name0)) { type Tag = name0.type }

  // ---- entry points that derive from the case class -----------------------

  def create(implicit pk: ColumnNames[PK], ck: ClusteringOf[CK]): CreateTable =
    CreateTable(
      this,
      columnsForA.columnDefs(naming),
      pk.names.map(naming.map),
      ck.columns.map(c => c.copy(name = naming.map(c.name)))
    )

  /** Select specific columns (all fields of `A` when none given). Nothing is constrained yet. */
  def select(
      cols: Column[_]*
  )(implicit pk: ColumnNames[PK], ck: ClusteringOf[CK]): Select[this.type, HNil, HNil, HNil] =
    Select[this.type, HNil, HNil, HNil](
      this,
      if (cols.isEmpty) columnsForA.columnDefs(naming).map(_.name) else cols.map(_.name),
      keyColumnNames(pk, ck)
    )

  private def keyColumnNames(pk: ColumnNames[PK], ck: ClusteringOf[CK]): Seq[String] =
    pk.names.map(naming.map) ++ ck.columns.map(c => naming.map(c.name))
}

object Table {

  /** Registry entry for a computed column: CQL name, CQL type, and renderer. */
  private[cql] final case class Computed[A](name: String, cqlType: String, render: A => String)

  /** Proof token returned by `Table.registerAllColumns`: its only constructor
    * is there, so implementing the abstract `columns` member forces the
    * all-fields-registered check.
    */
  final class AllColumns private[cql] ()
}
