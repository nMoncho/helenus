/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql.tables
package ddl

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.ResultSet
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps

final case class DropTable(table: TableDef, ifExistsFlag: Boolean = false) {

  def ifExists: DropTable = copy(ifExistsFlag = true)

  def execute()(implicit session: CqlSession): ResultSet =
    session.execute(toCQL)

  def executeAsync()(
      implicit session: CqlSession,
      @unused ec: ExecutionContext
  ): Future[AsyncResultSet] =
    session.executeAsync(toCQL).asScala

  def executeReactive()(implicit session: CqlSession): ReactiveResultSet =
    session.executeReactive(toCQL)

  def toCQL: String = {
    val ifExistsStr = if (ifExistsFlag) " IF EXISTS" else ""
    s"DROP TABLE$ifExistsStr ${table.fullTableName}"
  }

  override def toString: String = toCQL
}
