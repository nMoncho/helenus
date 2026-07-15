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
import net.nmoncho.helenus.api.cql.dml._
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

  def insert: Insert[this.type, HNil] = Insert[this.type](this)

  def update: Update[this.type, HNil, HNil, HNil, HNil, HNil] = Update[this.type](this)

  def delete: Delete[this.type, HNil, HNil, HNil, HNil, DeleteMode.Rows] = Delete[this.type](this)

  /** A typed column of this table. Instances are obtained with
    * `column("fieldName")`, which checks the field against the mapped case
    * class; `fieldName` is the case-class field, `name` the CQL column name
    * produced by the table's [[ColumnNamingScheme]].
    */
  class Column[T](val fieldName: String, val name: String, val frozen: Boolean)(
      implicit
      val codec: TypeCodec[T] // TODO not sure if making this a `val` is the best approach...
  ) {

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
    def containsKey(value: T): Predicate =
      Predicate(name, "CONTAINS KEY", codec.format(value))

    // ---- bind-marker variants (used with toFunction) ----------------------
    // Passing `?` instead of a value leaves a hole; the value arrives later
    // as an argument of the function produced by `toFunction`, typed as this
    // column's `T` (`in(?)` binds a whole `Seq[T]`).

    def ===(@unused m: BindMarker): EqBindPredicate[Tag, T] =
      new EqBindPredicate[Tag, T](name, codec)
    def >(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](name, ">", codec)
    def <(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](name, "<", codec)
    def >=(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](name, ">=", codec)
    def <=(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](name, "<=", codec)
    def !==(@unused m: BindMarker): FilterBindPredicate[T] =
      new FilterBindPredicate[T](name, "!=", codec)
    def in(@unused m: BindMarker): InBindPredicate[Tag, T] =
      new InBindPredicate[Tag, T](name, codec)

    // TODO add evidence that this column is a collection
    def contains(@unused m: BindMarker): FilterBindPredicate[T] =
      new FilterBindPredicate[T](name, "CONTAINS", codec)
    // TODO add evidence that this column is a collection
    def containsKey(@unused m: BindMarker): FilterBindPredicate[T] =
      new FilterBindPredicate(name, "CONTAINS KEY", codec)

    // ---- assignment (used in INSERT / UPDATE) -----------------------------

    def :=(value: T): Assignment = new Assignment(name, codec.format(value))

    /** A bound assignment: `col := ?` (value supplied via `toFunction`). */
    def :=(@unused m: BindMarker): BoundAssignment[T] = new BoundAssignment[T](name, codec)

    override def toString: String =
      s"Column($fieldName -> $name ${codec.getCqlType.asCql(frozen, false)})"

    def toCQL: String = s"$name ${codec.getCqlType.asCql(frozen, false)}"
  }

  /** A SET / VALUES assignment. */
  sealed class Assignment(val column: String, val value: String) {
    def toCQL: String             = s"$column = $value"
    override def toString: String = s"Assignment($toCQL)"
  }

  /** An assignment with a bound value (`col := ?`). The value arrives later
    * as an argument of the function produced by `toFunction`, typed as the
    * column's `V`.
    */
  final class BoundAssignment[V](column0: String, ct: TypeCodec[V])
      extends Assignment(column0, "?")
      with AssignmentHole {
    private[cql] def fill(v: Any): TableDef#Assignment =
      new Assignment(this.column, ct.format(v.asInstanceOf[V]))
  }

}

/** A table definition mapped to the case class `A`, which is the single
  * source of truth for the schema: DDL and full-row projections derive every
  * column from `A`'s fields (via [[ColumnsFor]]). Column vals are checked
  * references into `A`, and [[registerAllColumns]] enforces the other
  * direction: every field of `A` must have a declared column, so case class
  * and table can never drift in either direction.
  *
  * {{{
  * case class Users(id: UUID, username: String, age: Int)
  *
  * object UsersTable extends Table[Users]("ks", "users") {
  *   val id       = column[UUID]("id")         // checked: Users.id must be a UUID
  *   val username = column[String]("username") // a typo'd name or type does not compile
  *   val age      = column[Int]("age")
  *
  *   // checked: adding a field to Users without a column val fails here
  *   protected val columns = registerAllColumns(id :: username :: age :: HNil)
  *
  *   type PK = id.Tag :: HNil
  *   type CK = username.Tag :: HNil
  * }
  * }}}
  *
  * Field names are translated to CQL column names with [[naming]] (e.g.
  * `deviceId` to `device_id` under [[SnakeCase]]).
  *
  * A table may also declare COMPUTED columns with [[computedColumn]]: stored
  * columns derived from `A`'s fields but not fields themselves. They can be
  * queried / used in keys like any column, and are written automatically by
  * [[insertFrom]]; the [[RowMapper]] ignores them (it reads `A`'s fields by
  * name, and never asks for a computed column).
  *
  * The schema derivations ([[ColumnsFor]], [[InsertValues]]) are resolved
  * ONCE, as constructor implicits, at the `object X extends Table[A](...)`
  * definition, where `A` is concrete. This is also where a field of `A`
  * lacking a `CQLType` fails to compile, and it keeps every query entry point
  * (`create` / `select` / `insertFrom`) free of shapeless implicits, which
  * IDEs with weaker implicit search would flag at each call site.
  */
abstract class Table[A](keyspace0: String, tableName0: String)(
    implicit columnsForA: ColumnsFor[A],
    insertValuesForA: InsertValues[A]
) extends TableDef(keyspace0, tableName0) {

  /** Registry entry for a computed column: CQL name, CQL type, and renderer. */
  class ComputedColumn[T: TypeCodec](name: String, val cqlType: String, val render: A => T)
      extends Column[T](name, name, false)

  /** How case-class field names map to CQL column names. */
  protected def naming: ColumnNamingScheme = DefaultColumnNamingScheme

  /** Registered columns declared on this table, that are not computed, in declaration order. */
  private val registeredColumns = scala.collection.mutable.ListBuffer.empty[Column[_]]

  /** Computed columns declared on this table, in declaration order. */
  private val computedColumns = scala.collection.mutable.ListBuffer.empty[ComputedColumn[_]]

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
  protected def column[V: TypeCodec](name0: String with Singleton, frozen: Boolean = false)(
      implicit @unused field: FieldOfType[A, name0.type, V]
  ): Column[V] { type Tag = name0.type } = {
    val col = new Column[V](name0, naming.map(name0), frozen) { type Tag = name0.type }

    registeredColumns += col

    col
  }

  /** Declare a computed column: a stored column whose value is derived from an
    * `A` via `compute`. Unlike [[column]] it is not a field of `A`, so it is
    * skipped by the [[RowMapper]], but it participates in DDL, `insertFrom`,
    * keys, and query predicates like any other column.
    */
  protected def computedColumn[Col](
      name0: String with Singleton,
      frozen: Boolean = false
  )(compute: A => Col)(implicit ct: TypeCodec[Col]): Column[Col] { type Tag = name0.type } = {
    val cqlName = naming.map(name0)
    computedColumns += new ComputedColumn(
      cqlName,
      ct.getCqlType.asCql(frozen, false),
      a => compute(a)
    )
    new Column[Col](name0, cqlName, frozen) { type Tag = name0.type }
  }

  // ---- entry points that derive from the case class -----------------------

  def create(implicit pk: ColumnNames[PK], ck: ClusteringOf[CK]): CreateTable =
    CreateTable(
      this,
      (registeredColumns ++ computedColumns).toSeq,
      pk.names.map(naming.map),
      ck.columns.map(c => c.copy(name = naming.map(c.name)))
    )

  /** Select specific columns (all fields of `A` when none given). Nothing is constrained yet. */
  def select(
      cols: Column[_]*
  )(implicit pk: ColumnNames[PK], ck: ClusteringOf[CK]): Select[this.type, HNil, HNil, HNil, HNil] =
    Select[this.type, HNil, HNil, HNil, HNil](
      this,
      cols.map(_.name),
      keyColumnNames(pk, ck)
    )

  /** Insert a whole entity: writes every field of `A` plus every computed
    * column (filled by its `compute` function). The returned builder can be
    * refined further (`ifNotExists`, `usingTTL`, extra `value(...)`).
    */
  def insertFrom(a: A): Insert[this.type, HNil] = {
    val fieldAssignments =
      insertValuesForA.values(a, naming).map { case (n, v) => new Assignment(n, v) }
    val computedAssignments = computedColumns.toList.map(c =>
      new Assignment(c.name, c.render(a).toString)
    ) // FIXME this isn't going to work
    Insert[this.type](this).copy(assignments = fieldAssignments ++ computedAssignments)
  }

  private def keyColumnNames(pk: ColumnNames[PK], ck: ClusteringOf[CK]): Seq[String] =
    pk.names.map(naming.map) ++ ck.columns.map(c => naming.map(c.name))
}

object Table {

  /** Proof token returned by `Table.registerAllColumns`: its only constructor
    * is there, so implementing the abstract `columns` member forces the
    * all-fields-registered check.
    */
  final class AllColumns private[cql] ()
}

/** Runtime side of a bound assignment (`col := ?`), fillable later with an
  * argument of the captured column type.
  */
sealed trait AssignmentHole {
  private[cql] def fill(v: Any): TableDef#Assignment
}
