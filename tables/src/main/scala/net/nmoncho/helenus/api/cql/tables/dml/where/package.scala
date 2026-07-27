/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables.dml

package object where {

  /** Marker for values accepted by `where`: single predicates and conjunctions
    * built with `and`.
    */
  trait WhereClause

  /** Phantom marker recorded when a predicate can never be part of a valid
    * primary-key restriction (`!==`, `contains`). Its presence in the
    * constraint state makes the ungated `execute` unavailable, leaving only
    * `allowFiltering.execute`.
    */
  sealed trait RequiresFiltering
}
