/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import com.datastax.oss.driver.api.core.cql.BoundStatement
import net.nmoncho.helenus.api.cql.dml.where.BindPredicate
import net.nmoncho.helenus.api.cql.dml.where.BoundPredicate
import net.nmoncho.helenus.api.cql.dml.where.Predicate

package object dml {

  def bindBoundPredicates(
      bstmt: BoundStatement,
      predicates: Seq[BoundPredicate[_, _]],
      offset: Int = 0
  ): BoundStatement =
    predicates.zipWithIndex
      .foldLeft(bstmt) { case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + offset, p.value)
      }

  def bindPredicates(
      bstmt: BoundStatement,
      predicates: Seq[Predicate[_, _]],
      values: Iterator[Any],
      offset: Int = 0
  ): BoundStatement =
    predicates.zipWithIndex.foldLeft(bstmt) {
      case (bstmt, (p: BoundPredicate[Any, Any], idx)) =>
        p.bind(bstmt, idx + offset, p.value)

      case (bstmt, (p: BindPredicate[_, Any], idx)) =>
        p.bind(bstmt, idx + offset, values.next())
    }

}
