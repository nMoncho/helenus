/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.utils

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock

/** Builds a Mockito-backed [[Row]] from an ordered list of `(column, value)`
  * pairs, so columns can be read both by name and by index (index = position
  * in the given list).
  *
  * `get` returns the mapped value regardless of the codec (the codec is
  * ignored), and the primitive accessors (`getInt`, `getBoolean`, ...) and
  * `isNull` answer from the same data. This is enough to exercise
  * `RowMapper`/`ColumnMapper` wiring without a live Cassandra connection or a
  * real driver codec.
  *
  * A column that is absent, or present with a `null` value, is reported as a
  * null column by `isNull`.
  */
object TestRow {

  def apply(columns: (String, Any)*): Row = {
    val row     = mock(classOf[Row])
    val byName  = columns.toMap
    val byIndex = columns.map(_._2).toVector

    def valueByName(inv: InvocationOnMock): Any = {
      val name = inv.getArgument[String](0)
      byName.getOrElse(
        name,
        throw new NoSuchElementException(s"no fake value set for column '$name'")
      )
    }

    // get(name, codec)
    when(row.get(anyString(), any[TypeCodec[Any]]())).thenAnswer { (inv: InvocationOnMock) =>
      valueByName(inv).asInstanceOf[AnyRef]
    }

    // get(index, codec)
    when(row.get(anyInt(), any[TypeCodec[Any]]())).thenAnswer { (inv: InvocationOnMock) =>
      byIndex(inv.getArgument[Int](0)).asInstanceOf[AnyRef]
    }

    // primitive accessors, by name
    when(row.getString(anyString())).thenAnswer((inv: InvocationOnMock) =>
      valueByName(inv).asInstanceOf[String]
    )
    when(row.getInt(anyString())).thenAnswer((inv: InvocationOnMock) =>
      valueByName(inv).asInstanceOf[Int]
    )
    when(row.getBoolean(anyString())).thenAnswer((inv: InvocationOnMock) =>
      valueByName(inv).asInstanceOf[Boolean]
    )

    // isNull(name): a column is null when it is absent or explicitly null
    when(row.isNull(anyString())).thenAnswer { (inv: InvocationOnMock) =>
      val name = inv.getArgument[String](0)
      !byName.contains(name) || byName(name) == null
    }

    row
  }
}
