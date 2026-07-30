/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import shapeless.::
import shapeless.HList
import shapeless.HNil

/** Phantom marker: clustering column with name tag `C` sorted ascending (the
  * CQL default). Used inside a table's `CK` declaration; a bare tag means the
  * same.
  */
sealed trait Asc[C]

/** Phantom marker: clustering column with name tag `C` sorted descending. */
sealed trait Desc[C]

/** A clustering column name together with its declared sort direction. */
final case class ClusteringSpec(name: String, descending: Boolean) {
  def direction: String = if (descending) "DESC" else "ASC"
}

/** A query-time ORDER BY entry: a column and its sort direction. Built with
  * `column.asc` / `column.desc`, never from raw strings.
  */
final case class ColumnOrder(column: String, descending: Boolean) {
  def direction: String = if (descending) "DESC" else "ASC"
  def toCQL: String     = s"$column $direction"
}

/** Materializes the runtime clustering columns, with their sort directions,
  * from a table's `CK` type. Elements may be a bare column name tag
  * (ascending by default) or wrapped in [[Asc]] / [[Desc]].
  */
trait ClusteringOf[L <: HList] {
  def columns: List[ClusteringSpec]
}

object ClusteringOf {

  implicit val hnil: ClusteringOf[HNil] = new ClusteringOf[HNil] {
    def columns: List[ClusteringSpec] = Nil
  }

  /** A bare column tag defaults to ascending. */
  implicit def bareHead[H <: String, T <: HList](
      implicit head: ValueOf[H],
      tail: ClusteringOf[T]
  ): ClusteringOf[H :: T] = new ClusteringOf[H :: T] {
    def columns: List[ClusteringSpec] =
      ClusteringSpec(head.value, descending = false) :: tail.columns
  }

  implicit def ascHead[C <: String, T <: HList](
      implicit head: ValueOf[C],
      tail: ClusteringOf[T]
  ): ClusteringOf[Asc[C] :: T] = new ClusteringOf[Asc[C] :: T] {
    def columns: List[ClusteringSpec] =
      ClusteringSpec(head.value, descending = false) :: tail.columns
  }

  implicit def descHead[C <: String, T <: HList](
      implicit head: ValueOf[C],
      tail: ClusteringOf[T]
  ): ClusteringOf[Desc[C] :: T] = new ClusteringOf[Desc[C] :: T] {
    def columns: List[ClusteringSpec] =
      ClusteringSpec(head.value, descending = true) :: tail.columns
  }
}
