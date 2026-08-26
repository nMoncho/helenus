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

/** Adapts a Helenus [[RowMapper]] into the connector's [[RowReaderFactory]] SPI (B1).
  *
  * The connector maps rows through its own `RowReaderFactory[T]` / `RowReader[T]` SPI,
  * which knows nothing about Helenus codecs, derivation, or UDT handling. This factory
  * bridges the gap: the connector still owns the token-aware scan, the split model, and
  * predicate pushdown, while Helenus owns only the `Row => T` conversion, done by the
  * user's `RowMapper[T]` and its codecs (UDT and collection codecs included). The `Row`
  * the connector hands to [[RowReader.read]] is `com.datastax.oss.driver.api.core.cql.Row`,
  * the exact type [[RowMapper.apply]] consumes, so the bridge is a direct delegation.
  *
  * ==Why a provider, not a materialized mapper==
  *
  * The connector calls [[rowReader]] on the '''driver''', stores the returned
  * [[RowReader]] as a field of its scan RDD, and serializes that RDD to every executor.
  * `RowMapper` extends `Serializable`, but a ''derived'' mapper closes over driver
  * `TypeCodec` instances (`StringCodec`, the UDT codec, …) which are '''not''' serializable,
  * so a reader holding a materialized mapper fails at runtime with
  * `NotSerializableException`. Both this factory and the [[HelenusRowReader]] it returns
  * therefore hold only a serializable `mapperProvider` (a Scala lambda) plus, here, the
  * `ClassTag`. The mapper is derived by a `@transient lazy val` inside the reader, forced
  * only by `read` on the executor. Never when the connector builds and ships the reader.
  * It is thus derived once per partition (once per deserialized reader) and reused for
  * every row. Binding the mapper to a companion `val` (or `RowMapper.cached[T]()`) makes
  * the actual derivation run once per executor JVM, the provider merely handing it back.
  *
  * @param mapperProvider re-evaluated on the executor to obtain the row mapper
  * @param ct             provides `targetClass`, which the connector uses to type the scan
  */
final class HelenusRowReaderFactory[T](mapperProvider: () => RowMapper[T])(
    implicit ct: ClassTag[T]
) extends RowReaderFactory[T]
    with Serializable {

  override def rowReader(table: TableDef, selectedColumns: IndexedSeq[ColumnRef]): RowReader[T] =
    new HelenusRowReader[T](mapperProvider)

  override def targetClass: Class[T] =
    ct.runtimeClass.asInstanceOf[Class[T]]
}

object HelenusRowReaderFactory {

  /** Builds a factory from a by-name mapper expression.
    *
    * Pass the derivation itself `RowMapper.of[T]`, `RowMapper.cached[T]()`, or a
    * companion `val` such as `Hotel.rowMapper`, rather than a value captured from an
    * enclosing scope. The expression is re-evaluated on the executor, so the mapper and
    * its non-serializable driver codecs are rebuilt there instead of being shipped.
    */
  def apply[T](mapper: => RowMapper[T])(implicit ct: ClassTag[T]): HelenusRowReaderFactory[T] =
    new HelenusRowReaderFactory[T](() => mapper)
}

/** The connector's [[RowReader]] backed by a Helenus [[RowMapper]].
  *
  * The reader is built on the driver and serialized to executors, so it holds only the
  * serializable `mapperProvider`; the mapper itself is a `@transient lazy val` forced by
  * the first `read` on the executor (derived once per partition, reused per row) and never
  * crosses the wire with its non-serializable driver codecs. `read` then delegates
  * straight to `mapper.apply(row)`. `neededColumns` is `None`, and does not touch the
  * mapper, so column selection and pruning stay the connector's concern, never Helenus's.
  */
private[rdd] final class HelenusRowReader[T](mapperProvider: () => RowMapper[T])
    extends RowReader[T] {

  @transient private lazy val mapper: RowMapper[T] = mapperProvider()

  override def read(row: Row, rowMetaData: CassandraRowMetadata): T =
    mapper(row)

  override def neededColumns: Option[Seq[ColumnRef]] =
    None
}
