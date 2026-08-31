/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps

/** A `TRUNCATE TABLE` statement for a [[Table]] definition: removes every row
  * of the table while keeping the table and its schema.
  *
  * Obtain one from [[TableDef.truncate]] rather than constructing it directly.
  *
  * {{{
  * UsersTable.truncate.execute()
  * }}}
  *
  * @param table the table this statement truncates.
  */
final case class TruncateTable(table: TableDef) {

  /** Run this statement synchronously and return the driver [[ResultSet]]. */
  def execute()(implicit session: CqlSession): ResultSet =
    session.execute(toCQL)

  /** Run this statement asynchronously, completing with the [[AsyncResultSet]]. */
  def executeAsync()(
      implicit session: CqlSession,
      @unused ec: ExecutionContext
  ): Future[AsyncResultSet] =
    session.executeAsync(toCQL).asScala

  /** Run this statement reactively, returning a [[ReactiveResultSet]]. */
  def executeReactive()(implicit session: CqlSession): ReactiveResultSet =
    session.executeReactive(toCQL)

  /** Render this statement as a CQL string (also what [[toString]] returns). */
  def toCQL: String = s"TRUNCATE TABLE ${table.fullTableName}"

  override def toString: String = toCQL
}
