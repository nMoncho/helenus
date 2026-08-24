/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.rdd

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.spark.connector.CassandraRowMetadata
import com.datastax.spark.connector.ColumnRef
import com.datastax.spark.connector.cql.TableDef
import com.datastax.spark.connector.rdd.reader.RowReader
import com.datastax.spark.connector.rdd.reader.RowReaderFactory
import net.nmoncho.helenus.api.RowMapper

/** Adapts a Helenus [[RowMapper]] into the connector's [[RowReaderFactory]] SPI.
  *
  * The connector maps rows through its own `RowReaderFactory[T]` / `RowReader[T]` SPI,
  * which knows nothing about Helenus codecs, derivation, or UDT handling. This factory
  * bridges the gap: the connector still owns the token-aware scan, the split model, and
  * predicate pushdown, while Helenus owns only the `Row => T` conversion, done by the
  * user's `RowMapper[T]` and its codecs (UDT and collection codecs included).
  *
  * The `Row` the connector hands to [[RowReader.read]] is
  * `com.datastax.oss.driver.api.core.cql.Row` — the exact type
  * [[RowMapper.apply]] consumes — so the bridge is a direct delegation.
  *
  * The `RowMapper[T]` is materialized once, here, and closed over by the single
  * [[HelenusRowReader]] this factory returns for every partition. `RowMapper` (and its
  * `ColumnMapper`) extend `Serializable`, so that one instance ships to executors with
  * the reader rather than being re-derived per row or per partition. Bind the mapper to
  * a single `implicit val` (or `RowMapper.cached[T]()`) at the call site so the
  * (potentially expensive) derivation runs once per RDD.
  *
  * @param mapper the Helenus row mapper, derived once and reused for every row
  * @param ct     provides `targetClass`, which the connector uses to type the scan
  */
final class HelenusRowReaderFactory[T](
    implicit mapper: RowMapper[T],
    ct: ClassTag[T]
) extends RowReaderFactory[T]
    with Serializable {

  // One reader, one mapper: built here and reused across every partition, never per row.
  private val reader: RowReader[T] = new HelenusRowReader[T](mapper)

  override def rowReader(table: TableDef, selectedColumns: IndexedSeq[ColumnRef]): RowReader[T] =
    reader

  override def targetClass: Class[T] =
    ct.runtimeClass.asInstanceOf[Class[T]]
}

/** The connector's [[RowReader]] backed by a Helenus [[RowMapper]].
  *
  * `read` delegates straight to `mapper.apply(row)`. `neededColumns` is `None`: column
  * selection and pruning stay the connector's concern (it selects all columns unless the
  * scan narrows them), never Helenus's.
  */
private[rdd] final class HelenusRowReader[T](mapper: RowMapper[T])
    extends RowReader[T]
    with Serializable {

  override def read(row: Row, rowMetaData: CassandraRowMetadata): T =
    mapper(row)

  override def neededColumns: Option[Seq[ColumnRef]] =
    None
}
