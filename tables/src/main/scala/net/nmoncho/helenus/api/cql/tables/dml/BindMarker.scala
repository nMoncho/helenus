/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables.dml

/** The type of the [[?]] bind marker. Passing `?` to a column operator
  * instead of a value produces a predicate with a hole; `toFunction` turns
  * the statement into a `FunctionN` taking one argument per hole, in writing
  * order.
  */
sealed trait BindMarker

/** The bind marker: `where(UsersTable.age >= ? and UsersTable.age <= ?)`. */
case object ? extends BindMarker
