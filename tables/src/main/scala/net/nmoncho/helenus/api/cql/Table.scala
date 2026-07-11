/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import scala.annotation.unused

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.cql.ddl.DropTable
import shapeless.HList

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

  /** Table's full name with keyspace */
  def fullTableName: String = s"${keyspace}.${tableName}"

  // --------------- DDL ----------------

  def drop: DropTable = DropTable(this)

  /** A typed column of this table. Instances are obtained with
    * `column("fieldName")`, which checks the field against the mapped case
    * class; `fieldName` is the case-class field, `name` the CQL column name
    * produced by the table's [[net.nmoncho.helenus.api.ColumnNamingScheme]].
    */
  class Column[T](val fieldName: String, val name: String, val frozen: Boolean)(
      implicit codec: TypeCodec[T]
  ) {

    /** Type-level identity of this column: the literal type of the case-class
      * field name (e.g. `Tag = "id"`). Used in `PK` / `CK` declarations and
      * carried by predicates for the execute gates.
      */
    type Tag

    /** This column sorted ascending, e.g. `type CK = ts.Asc :: HNil` (same as a bare `ts.Tag`). */
    final type Asc = net.nmoncho.helenus.api.cql.Asc[Tag]

    /** This column sorted descending, e.g. `type CK = ts.Desc :: HNil`. */
    final type Desc = net.nmoncho.helenus.api.cql.Desc[Tag]

    // ---- query-time sort direction (for use in `Select.orderBy`) ---------

    /** Ascending ORDER BY clause for this column, e.g. `select().orderBy(ts.asc)`. */
    def asc: ColumnOrder = ColumnOrder(name, descending = false)

    /** Descending ORDER BY clause for this column, e.g. `select().orderBy(ts.desc)`. */
    def desc: ColumnOrder = ColumnOrder(name, descending = true)

    override def toString: String =
      s"Column($fieldName -> $name: ${codec.getCqlType.asCql(frozen, false)})"
  }
}

abstract class Table[A](keyspace: String, tableName: String)()
    extends TableDef(keyspace, tableName) {

  /** How case-class field names map to CQL column names. */
  protected def naming: ColumnNamingScheme = DefaultColumnNamingScheme

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
  ): Column[V] { type Tag = name0.type } =
    new Column[V](name0, naming.map(name0), frozen) { type Tag = name0.type }
}
