/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import scala.annotation.implicitNotFound
import scala.annotation.unused
import scala.collection.mutable

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.RowMapper.ColumnMapper
import net.nmoncho.helenus.api.tables.ddl._
import net.nmoncho.helenus.api.tables.dml._
import net.nmoncho.helenus.api.tables.dml.where._
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
      implicit val codec: TypeCodec[T]
  ) { self =>

    /** Type-level identity of this column: the literal type of the case-class
      * field name (e.g. `Tag = "id"`). Used in `PK` / `CK` declarations and
      * carried by predicates for the execute gates.
      */
    type Tag

    // ---- clustering sort direction (for use inside `type CK`) ------------

    /** This column sorted ascending, e.g. `type CK = ts.Asc :: HNil` (same as a bare `ts.Tag`). */
    final type Asc = net.nmoncho.helenus.api.tables.Asc[Tag]

    /** This column sorted descending, e.g. `type CK = ts.Desc :: HNil`. */
    final type Desc = net.nmoncho.helenus.api.tables.Desc[Tag]

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
    def ===(value: T): EqPredicate[Tag, T] = new EqPredicate[Tag, T](this, value)

    /** Range predicates. They also carry the column's field tag: a range is
      * allowed without ALLOW FILTERING only on the clustering column that
      * immediately follows the `===`-constrained prefix.
      */
    def >(value: T): RangePredicate[Tag, T]  = new RangePredicate[Tag, T](this, ">", value)
    def <(value: T): RangePredicate[Tag, T]  = new RangePredicate[Tag, T](this, "<", value)
    def >=(value: T): RangePredicate[Tag, T] = new RangePredicate[Tag, T](this, ">=", value)
    def <=(value: T): RangePredicate[Tag, T] = new RangePredicate[Tag, T](this, "<=", value)

    /** Never valid on a primary-key restriction: always requires ALLOW FILTERING. */
    def !==(value: T): Predicate[T, T] = Predicate(this, "!=", value)

    // FIXME Split DSL on predicates based on the column type

    // TODO check if we can actually be `V <: Iterable[T]` or we have to go to `Seq[T]`
    // Not sure if we can have any collection here, if Cassandra will support it
    /** Multi-value equality. Carries the column's field tag: CQL allows IN
      * only on the last component of the primary key (the gates check the
      * position per statement type).
      */
    def in[V <: Iterable[T]](values: V)(implicit iCodec: TypeCodec[V]): InPredicate[Tag, T, V] =
      new InPredicate[Tag, T, V](this, values, iCodec)

    /** `col CONTAINS value`: an element of a collection, or a value of a map (see [[ContainsValue]]). */
    def contains[V](value: V)(
        implicit @unused containsEv: ContainsValue[T, V],
        innerType: TypeCodec[V]
    ): Predicate[T, V] =
      new SingleValueOnCollectionPredicate(this, "CONTAINS", value, innerType)

    def containsKey[K, V](value: K)(
        implicit @unused ev: T <:< scala.collection.Map[K, V],
        innerType: TypeCodec[K]
    ): Predicate[T, K] =
      new SingleValueOnCollectionPredicate(this, "CONTAINS KEY", value, innerType)

    /** Map-entry equality: `col[key] = value`. Distinct from `containsKey`
      * (which only checks key presence): this pins the value at that key too.
      */
    def entry[K, V](key: K, value: V)(
        implicit ev: T <:< scala.collection.Map[K, V],
        keyCodec: TypeCodec[K],
        valueCodec: TypeCodec[V]
    ): Predicate[T, V] = new EntryPredicate[Tag, T, K, V](this, key, value)

    // ---- bind-marker variants (used with toFunction) ----------------------
    // Passing `?` instead of a value leaves a hole; the value arrives later
    // as an argument of the function produced by `prepare` and `prepareAsync`,
    // typed as this column's `T` (`in(?)` binds a whole `Seq[T]`).

    def ===(@unused m: BindMarker): EqBindPredicate[Tag, T] =
      new EqBindPredicate[Tag, T](this)
    def >(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](this, ">")
    def <(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](this, "<")
    def >=(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](this, ">=")
    def <=(@unused m: BindMarker): RangeBindPredicate[Tag, T] =
      new RangeBindPredicate[Tag, T](this, "<=")
    def !==(@unused m: BindMarker): BindPredicate[T, T] =
      new SingleValueBindPredicate[T](this, "!=")

    def in(@unused m: BindMarker)(
        implicit iCodec: TypeCodec[Seq[T]]
    ): InBindPredicate[Tag, T, Seq[T]] =
      new InBindPredicate[Tag, T, Seq[T]](this, iCodec)

    def contains[V](@unused m: BindMarker)(
        implicit @unused containsEv: ContainsValue[T, V],
        innerCodec: TypeCodec[V]
    ): BindPredicate[T, V] = new BindPredicate[T, V](this, "CONTAINS", innerCodec)

    def containsKey[K, V](@unused m: BindMarker)(
        implicit @unused ev: T <:< scala.collection.Map[K, V],
        innerCodec: TypeCodec[K]
    ): BindPredicate[T, K] = new BindPredicate[T, K](this, "CONTAINS KEY", innerCodec)

    /** Map-entry equality: `col[key] = value`. Distinct from `containsKey`
      * (which only checks key presence): this pins the value at that key too.
      */
    def entry[K, V](@unused k: BindMarker, @unused v: BindMarker)(
        implicit ev: T <:< scala.collection.Map[K, V],
        keyCodec: TypeCodec[K],
        valueCodec: TypeCodec[V],
        tupleCodec: TypeCodec[(K, V)]
    ): BindPredicate[T, (K, V)] = new EntryBindPredicate[T, K, V](this)

    // ---- assignment (used in INSERT / UPDATE) -----------------------------

    def :=(value: T): Assignment[T] = new BoundAssignment[T](this, value)

    /** A bound assignment: `col := ?` (value supplied via `toFunction`). */
    def :=(@unused m: BindMarker): BindAssignment[T] = new BindAssignment[T](this)

    override def toString: String =
      s"Column($fieldName -> $name ${codec.getCqlType.asCql(frozen, false)})"

    def toCQL: String = s"$name ${codec.getCqlType.asCql(frozen, false)}"
  }

  sealed trait Assignment[T] {
    def column: Column[T]

    final def toCQL: String = this match {
      case bound: BoundAssignment[_] => s"${column.name} = ${column.codec.format(bound.value)}"
      case _: BindAssignment[_] => s"${column.name} = ?"
    }

    override def toString: String = s"Assignment($toCQL)"
  }

  /** A SET / VALUES assignment. */
  class BoundAssignment[T](override val column: Column[T], val value: T) extends Assignment[T]

  /** An assignment with a bound value (`col := ?`). The value arrives later
    * as an argument of the function produced by `toFunction`, typed as the
    * column's `V`.
    */
  final class BindAssignment[T](override val column: Column[T]) extends Assignment[T]

}

/** A table definition mapped to the case class `A`, which is the single
  * source of truth for the schema: DDL and full-row projections derive every
  * column from `A`'s fields. Column vals are checked
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
  * The schema derivations ([[InsertValues]]) are resolved
  * ONCE, as constructor implicits, at the `object X extends Table[A](...)`
  * definition, where `A` is concrete. This is also where a field of `A`
  * lacking a `CQLType` fails to compile, and it keeps every query entry point
  * (`create` / `select` / `insertFrom`) free of shapeless implicits, which
  * IDEs with weaker implicit search would flag at each call site.
  */
abstract class Table[A](keyspace0: String, tableName0: String)(
    implicit insertValuesForA: InsertValues[A]
) extends TableDef(keyspace0, tableName0) {

  /** Registry entry for a computed column: CQL name, CQL type, and renderer. */
  class ComputedColumn[T: TypeCodec](name: String, frozen: Boolean, val render: A => T)
      extends Column[T](name, name, frozen) {

    def fill(a: A): Assignment[T] = new BoundAssignment[T](this, render(a))
  }

  /** Registry entry for a secondary index: index name and its CQL target (see [[IndexTargets]]). */
  class IndexDef(val name: String, val target: String, val kind: IndexKind = IndexKind.Secondary)

  /** How case-class field names map to CQL column names. */
  protected def naming: ColumnNamingScheme = DefaultColumnNamingScheme

  /** Registered columns declared on this table, that are not computed, in declaration order. */
  private val registeredColumns       = scala.collection.mutable.ListBuffer.empty[Column[_]]
  private val registeredColumnsByName = scala.collection.mutable.Map.empty[String, Column[_]]

  /** Computed columns declared on this table, in declaration order. */
  private val computedColumns = scala.collection.mutable.ListBuffer.empty[ComputedColumn[_]]

  /** Secondary indexes declared on this table (via [[index]]), in declaration order. */
  private val indexes = scala.collection.mutable.ListBuffer.empty[IndexDef]

  /** Every table must register its columns; implement as
    * `protected val columns = registerAllColumns(id :: ... :: HNil)`.
    *
    * This is the completeness half of the case-class contract: adding a field
    * to `A` without declaring (and registering) a column val for it fails to
    * compile here, because the registered list no longer matches `A`'s
    * fields. The reference half is checked per val by [[column]]. It also
    * carries [[rowMapper]], built from these exact columns (see
    * [[registerAllColumns]]).
    */
  protected def columns: Table.AllColumns[A]

  /** Reads a full row of `A` back, in the order [[registerAllColumns]] was
    * given its columns. Skips computed columns entirely, same as
    * [[computedColumn]] documents: they are not fields of `A`, so there is
    * nothing on `A` to fill them into.
    */
  def rowMapper: RowMapper[A] = columns.rowMapper

  /** Checks that `cols` lists a column for EVERY field of `A`, in field
    * declaration order, with matching value types. Computed columns are not
    * fields and must not be listed.
    */
  protected def registerAllColumns[L <: HList, R <: HList](
      @unused cols: L
  )(
      implicit @unused gen: Generic.Aux[A, R],
      @unused covers: CoversFields[L, R]
  ): Table.AllColumns[A] =
    new Table.AllColumns[A]((row: Row) => gen.from(covers.readRow(cols, row)))

  /** Witnesses that `L` is a list of this table's columns whose value types
    * are exactly `R` (the field types of `A`), in order.
    */
  @implicitNotFound(
    "The registered columns ${L} do not cover the fields of the case class " +
      "(expected value types ${R}, in field declaration order). " +
      "Declare a column val for every field and list them all in registerAllColumns."
  )
  protected sealed trait CoversFields[L <: HList, R <: HList] {
    def readRow(cols: L, row: Row): R
  }

  protected object CoversFields {

    implicit val nil: CoversFields[HNil, HNil] = new CoversFields[HNil, HNil] {
      def readRow(cols: HNil, row: Row): HNil = HNil
    }

    implicit def cons[H, C <: Column[H], LT <: HList, RT <: HList](
        implicit columnMapper: ColumnMapper[H],
        rest: CoversFields[LT, RT]
    ): CoversFields[C :: LT, H :: RT] = new CoversFields[C :: LT, H :: RT] {
      def readRow(cols: C :: LT, row: Row): H :: RT =
        columnMapper(cols.head.name, row) :: rest.readRow(cols.tail, row)
    }
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
  protected def column[V](name0: String with Singleton, frozen: Boolean = false)(
      implicit @unused field: FieldOfType[A, name0.type, V],
      codec: TypeCodec[V]
  ): Column[V] { type Tag = name0.type } = {
    val col = new Column[V](name0, naming.apply(name0), frozen) { type Tag = name0.type }

    registeredColumns += col
    registeredColumnsByName += name0 -> col

    col
  }

  /** Reference a [[Frozen]] field of `A` as a column, stating only the
    * WRAPPED type: `frozenColumn[Set[String]]("tags")` for a field declared
    * as `tags: Frozen[Set[String]]`. Exactly `column[Frozen[V]](name0)`
    * (same checks, same DDL, same `frozen<...>` rendering) but without
    * having to spell `Frozen[...]` out again at the call site — the field
    * itself must still be declared `Frozen[V]`, since that's what makes the
    * case class the source of truth for the `frozen<...>` DDL type.
    */
  protected def frozenColumn[V: TypeCodec](name0: String with Singleton)(
      implicit field: FieldOfType[A, name0.type, Frozen[V]]
  ): Column[Frozen[V]] { type Tag = name0.type } =
    column[Frozen[V]](name0, true)

  /** Declare a computed column: a stored column whose value is derived from an
    * `A` via `compute`. Unlike [[column]] it is not a field of `A`, so it is
    * skipped by the [[RowMapper]], but it participates in DDL, `insertFrom`,
    * keys, and query predicates like any other column.
    */
  protected def computedColumn[Col](
      name0: String with Singleton,
      frozen: Boolean = false
  )(compute: A => Col)(implicit ct: TypeCodec[Col]): Column[Col] { type Tag = name0.type } = {
    val col = new ComputedColumn(naming.apply(name0), frozen, compute) { type Tag = name0.type }

    computedColumns += col

    col
  }

  /** Declare a secondary index on `col`, giving evidence that `col.contains`
    * (and, for a map column, `col.containsKey` / `col.entry`) can be
    * satisfied by CQL directly through the index: `execute` no longer
    * requires `allowFiltering` for it (see [[TableDef.Indexed]]). Also
    * registers the index(es) so they can be created with [[createIndexes]] —
    * a map column registers a values index (for `contains`), a `KEYS(...)`
    * index (for `containsKey`), AND an `ENTRIES(...)` index (for `entry`),
    * all three at once; a [[Frozen]] column registers a single `FULL(...)`
    * index instead, since a frozen collection has no per-element indexing
    * (and none of `contains` / `containsKey` / `entry` either — see
    * [[IndexTargets]]).
    *
    * The index name defaults to `tableName_columnName` (suffixed per target,
    * see below); pass `name` to override that base, e.g. because two indexes
    * would otherwise collide, or to match a name already used in production.
    *
    * `kind` picks the index implementation: [[IndexKind.Secondary]] (the
    * default, the database's built-in index) or [[IndexKind.Custom]] (e.g.
    * Storage-Attached Indexing — see [[SAI]] for common `USING` classes).
    * It only affects the DDL [[createIndexes]] generates; the `contains` /
    * `containsKey` / `entry` / `===` exemption from `allowFiltering` applies
    * the same way regardless of kind.
    *
    * {{{
    * val tags   = index(column[Set[String]]("tags"))
    * val labels = index(frozenColumn[Set[String]]("labels"))
    * val email  = index(column[String]("email"), name = Some("users_email_lookup"))
    * val bio    = index(column[String]("bio"), kind = IndexKind.Custom(SAI.openSource))
    * }}}
    *
    * A column needing more than one physical index (a map registers both a
    * values and a `KEYS(...)` index) always keeps a distinguishing suffix on
    * `name`, since one name can't cover two indexes; a single-target column
    * uses `name` verbatim when given.
    */
  protected def index[V: TypeCodec](
      col: Column[V],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  )(implicit targets: IndexTargets[V]): (Column[V] with Indexed[V]) { type Tag = col.Tag } = {
    registerIndexTargets(col.name, targets.targets(col.name), name, kind)
    new Column[V](col.fieldName, col.name, false) with Indexed[V] {
      type Tag = col.Tag
    }
  }

  /** Registers the CREATE INDEX statement(s) for the given targets, applying
    * the same naming rule `index` documents above: a single target uses
    * `name` verbatim when given, several targets always keep a
    * distinguishing suffix (since one name can't cover more than one index).
    */
  private def registerIndexTargets(
      colName: String,
      idxTargets: List[(String, String)],
      name: Option[String],
      kind: IndexKind
  ): Unit = {
    val baseName = name.getOrElse(s"${tableName}_$colName")
    idxTargets.foreach { case (suffix, target) =>
      val indexName =
        if (name.isDefined && idxTargets.size == 1) baseName else s"${baseName}_$suffix"
      indexes += new IndexDef(indexName, target, kind)
    }
  }

  private def valuesTarget(colName: String): List[(String, String)] = List("idx" -> colName)
  private def keysTarget(colName: String): List[(String, String)]   = List(
    "keys_idx" -> s"KEYS($colName)"
  )
  private def entriesTarget(colName: String): List[(String, String)] = List(
    "entries_idx" -> s"ENTRIES($colName)"
  )

  /** Declare a secondary index covering only SOME of a map column's
    * independently indexable aspects, instead of the no-choice `index(col)`
    * (which grants everything at once — `contains`, `containsKey`, AND
    * `entry`). Pick the method matching what physical index(es) you actually
    * have (or will have): [[indexValues]] alone backs `contains`,
    * [[indexKeys]] alone backs `containsKey`, [[indexEntries]] alone backs
    * `entry`, and [[indexValuesAndKeys]] / [[indexValuesAndEntries]] /
    * [[indexKeysAndEntries]] grant two at once. Only the predicate method(s)
    * backed by a chosen aspect are exempted from `allowFiltering`; the
    * others keep requiring it, exactly as if `col` had never been indexed
    * for them:
    *
    * {{{
    * val labels = indexKeys(column[Map[String, String]]("labels"))
    * // labels.containsKey(...) is now allowFiltering-free; labels.contains(...)
    * // and labels.entry(...) still require it, same as an unindexed column.
    * }}}
    */
  protected def indexValues[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with ValuesIndexed[Map[K, V]]) { type Tag = col.Tag } = {
    registerIndexTargets(col.name, valuesTarget(col.name), name, kind)
    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec) with ValuesIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  protected def indexKeys[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with KeysIndexed[Map[K, V]]) { type Tag = col.Tag } = {
    registerIndexTargets(col.name, keysTarget(col.name), name, kind)
    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec) with KeysIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  protected def indexEntries[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with EntriesIndexed[Map[K, V]]) { type Tag = col.Tag } = {
    registerIndexTargets(col.name, entriesTarget(col.name), name, kind)
    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec)
      with EntriesIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  protected def indexValuesAndKeys[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with ValuesIndexed[Map[K, V]] with KeysIndexed[Map[K, V]]) {
    type Tag = col.Tag
  } = {
    registerIndexTargets(col.name, valuesTarget(col.name) ++ keysTarget(col.name), name, kind)
    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec)
      with ValuesIndexed[Map[K, V]]
      with KeysIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  protected def indexValuesAndEntries[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with ValuesIndexed[Map[K, V]] with EntriesIndexed[Map[K, V]]) {
    type Tag = col.Tag
  } = {
    registerIndexTargets(col.name, valuesTarget(col.name) ++ entriesTarget(col.name), name, kind)
    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec)
      with ValuesIndexed[Map[K, V]]
      with EntriesIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  protected def indexKeysAndEntries[K, V](
      col: Column[Map[K, V]],
      name: Option[String] = None,
      kind: IndexKind      = IndexKind.Secondary
  ): (Column[Map[K, V]] with KeysIndexed[Map[K, V]] with EntriesIndexed[Map[K, V]]) {
    type Tag = col.Tag
  } = {
    registerIndexTargets(col.name, keysTarget(col.name) ++ entriesTarget(col.name), name, kind)

    new Column[Map[K, V]](col.fieldName, col.name, false)(col.codec)
      with KeysIndexed[Map[K, V]]
      with EntriesIndexed[Map[K, V]] {
      type Tag = col.Tag
    }
  }

  // ---- entry points that derive from the case class -----------------------

  def create(implicit pk: ColumnNames[PK], ck: ClusteringOf[CK]): CreateTable =
    CreateTable(
      this,
      (registeredColumns ++ computedColumns).toSeq,
      pk.names.map(naming.apply),
      ck.columns.map(c => c.copy(name = naming.apply(c.name)))
    )

  /** CREATE INDEX statements for every index declared with [[index]]. */
  def createIndexes: Seq[CreateIndex] =
    indexes.toList.map(i => CreateIndex(this, i.name, i.target, i.kind))

  // format: off

  def select[T1](col1: Column[T1])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[T1]
  ): Select[this.type, HNil, HNil, HNil, HNil, T1] =
    Select[this.type, T1](this, Seq(col1), keyColumnNames(pk, ck))

  def select[T1, T2](col1: Column[T1], col2: Column[T2])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2)] =
    Select[this.type, (T1, T2)](this, Seq(col1, col2), keyColumnNames(pk, ck))

  def select[T1, T2, T3](col1: Column[T1], col2: Column[T2], col3: Column[T3])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3)] =
    Select[this.type, (T1, T2, T3)](this, Seq(col1, col2, col3), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4)] =
    Select[this.type, (T1, T2, T3, T4)](this, Seq(col1, col2, col3, col4), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5)] =
    Select[this.type, (T1, T2, T3, T4, T5)](this, Seq(col1, col2, col3, col4, col5), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6)](this, Seq(col1, col2, col3, col4, col5, col6), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7)](this, Seq(col1, col2, col3, col4, col5, col6, col7), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17], col18: Column[T18])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17, col18), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17], col18: Column[T18], col19: Column[T19])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17, col18, col19), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17], col18: Column[T18], col19: Column[T19], col20: Column[T20])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17, col18, col19, col20), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17], col18: Column[T18], col19: Column[T19], col20: Column[T20], col21: Column[T21])(
    implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17, col18, col19, col20, col21), keyColumnNames(pk, ck))

  def select[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](col1: Column[T1], col2: Column[T2], col3: Column[T3], col4: Column[T4], col5: Column[T5], col6: Column[T6], col7: Column[T7], col8: Column[T8], col9: Column[T9], col10: Column[T10], col11: Column[T11], col12: Column[T12], col13: Column[T13], col14: Column[T14], col15: Column[T15], col16: Column[T16], col17: Column[T17], col18: Column[T18], col19: Column[T19], col20: Column[T20], col21: Column[T21], col22: Column[T22])(
      implicit pk: ColumnNames[PK], ck: ClusteringOf[CK], rowMapper: RowMapper[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)]
  ): Select[this.type, HNil, HNil, HNil, HNil, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)] =
    Select[this.type, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)](this, Seq(col1, col2, col3, col4, col5, col6, col7, col8, col9, col10, col11, col12, col13, col14, col15, col16, col17, col18, col19, col20, col21, col22), keyColumnNames(pk, ck))
  // format: on

  def select()(
      implicit pk: ColumnNames[PK],
      ck: ClusteringOf[CK]
  ): Select[this.type, HNil, HNil, HNil, HNil, A] =
    Select[this.type, A](
      this,
      Nil,
      keyColumnNames(pk, ck)
    )(rowMapper)

  /** Insert a whole entity: writes every field of `A` plus every computed
    * column (filled by its `compute` function). The returned builder can be
    * refined further (`ifNotExists`, `usingTTL`, extra `value(...)`).
    */
  def insertFrom(a: A): Insert[this.type, HNil] = {
    val fieldAssignments = insertValuesForA.values(
      a,
      registeredColumnsByName.asInstanceOf[mutable.Map[String, TableDef#Column[_]]],
      naming
    )

    val computedAssignments = computedColumns.toList.map(_.fill(a))

    Insert[this.type](this).copy(assignments = fieldAssignments ++ computedAssignments)
  }

  private def keyColumnNames(pk: ColumnNames[PK], ck: ClusteringOf[CK]): Seq[String] =
    pk.names.map(naming.apply) ++ ck.columns.map(c => naming.apply(c.name))
}

object Table {

  /** Proof token returned by `Table.registerAllColumns`: its only constructor
    * is there, so implementing the abstract `columns` member forces the
    * all-fields-registered check. Also carries the [[RowMapper]] built from
    * that same call, exposed by `Table.rowMapper`.
    */
  final class AllColumns[A] private[tables] (private[tables] val rowMapper: RowMapper[A])
}
