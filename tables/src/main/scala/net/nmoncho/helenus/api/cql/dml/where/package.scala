/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.dml

package object where {

  /** Marker for values accepted by `where`: single predicates and conjunctions
    * built with `and`.
    */
  trait WhereClause

}
