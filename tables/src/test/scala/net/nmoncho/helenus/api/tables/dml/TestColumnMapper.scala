/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables.dml

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/** Test-only stand-in for the real `ColumnMapper[V]` catalog (assumed to
  * already exist elsewhere, per the driver's `TypeCodec` registry): a single
  * fully generic instance that reads a real (mocked) `Row` through its
  * generic `get(name, Class)` accessor. [[apply]] builds that mock, stubbing
  * `get` to answer from the given `Map` by column name — enough to exercise
  * `Table.rowMapper`'s wiring (does it read the right column, by the right
  * name, in the right order?) against a genuine `Row`, without a live
  * Cassandra connection or a real driver codec.
  */
object TestRow {

  /** A mocked `Row` whose `get(columnName, targetClass)` answers from `values`, by column name. */
  def apply(values: (String, Any)*): Row = {
    val row  = Mockito.mock(classOf[Row])
    val data = values.toMap

    val answer: Answer[AnyRef] = (invocation: InvocationOnMock) => {
      val columnName = invocation.getArgument[String](0)
      data
        .getOrElse(
          columnName,
          throw new NoSuchElementException(s"no fake value set for column '$columnName'")
        )
        .asInstanceOf[AnyRef]
    }

    Mockito
      .when(row.get(ArgumentMatchers.anyString(), ArgumentMatchers.any[TypeCodec[AnyRef]]()))
      .thenAnswer(answer)

    row
  }
}
