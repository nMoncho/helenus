/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

/** The kind of secondary index `Table.index` creates: the database's
  * built-in index, or a custom index backed by an index implementation class
  * (e.g. Storage-Attached Indexing).
  */
sealed trait IndexKind

object IndexKind {

  /** The built-in secondary index: `CREATE INDEX ...`. */
  case object Secondary extends IndexKind

  /** A custom index: `CREATE CUSTOM INDEX ... USING '<usingClass>'
    * [WITH OPTIONS = {...}]`.
    *
    * `usingClass` is deliberately a free-form string rather than a fixed set
    * of choices: which classes are available, and under what name, depends on
    * the cluster (e.g. SAI ships under a different class name in open-source
    * Cassandra than in DataStax Enterprise, see [[SAI]] for both).
    */
  final case class Custom(usingClass: String, options: Map[String, String] = Map.empty)
      extends IndexKind
}

/** Common `USING` class names for Storage-Attached Indexing (SAI), to pass
  * into [[IndexKind.Custom]]: `index(col, kind = IndexKind.Custom(SAI.openSource))`.
  */
object SAI {

  /** Apache Cassandra 5.0+. */
  val openSource: String = "org.apache.cassandra.index.sai.StorageAttachedIndex"

  /** DataStax Enterprise 6.8+. */
  val dse: String = "StorageAttachedIndex"
}
