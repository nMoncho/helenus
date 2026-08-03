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

/** A `DROP TABLE` statement for a [[Table]] definition.
  *
  * Obtain one from [[TableDef.drop]] rather than constructing it directly.
  *
  * {{{
  * UsersTable.drop.ifExists.execute()
  * }}}
  *
  * @param table        the table this statement drops.
  * @param ifExistsFlag whether to emit `IF EXISTS` (set via [[ifExists]]).
  */
final case class DropTable(table: TableDef, ifExistsFlag: Boolean = false) {

  /** Emit `DROP TABLE IF EXISTS`, making the statement a no-op when the table
    * does not exist instead of failing.
    */
  def ifExists: DropTable = copy(ifExistsFlag = true)

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
  def toCQL: String = {
    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""
    s"DROP TABLE$ifExistsStr ${table.fullTableName}"
  }

  override def toString: String = toCQL
}
