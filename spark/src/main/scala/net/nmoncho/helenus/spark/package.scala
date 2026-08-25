/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.spark.connector.rdd.reader.RowReaderFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import net.nmoncho.helenus.spark.rdd.HelenusRowReaderFactory
import net.nmoncho.helenus.spark.sink.CassandraSink
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset

package object spark {

  /** Convenience entry point for the Helenus read bridge (B2).
    *
    * Bind the result as an implicit `RowReaderFactory[T]` next to the `sc.cassandraTable[T]`
    * call so the connector maps rows through Helenus codecs while still owning the
    * token-aware scan:
    *
    * {{{
    * import net.nmoncho.helenus._
    * import net.nmoncho.helenus.spark._
    * import com.datastax.spark.connector._
    *
    * implicit val hotels: RowReaderFactory[Hotel] = helenusRowReaderFactory(Hotel.rowMapper)
    * val rdd = sc.cassandraTable[Hotel]("hotels_ks", "hotels")
    * }}}
    *
    * ==Precedence==
    *
    * A locally bound `implicit val` sits in the lexical scope, which outranks the
    * connector's own `RowReaderFactory` implicits (`classBasedRowReaderFactory`, in the
    * companion / implicit scope), so `sc.cassandraTable[T]` resolves this factory
    * unambiguously even with `import com.datastax.spark.connector._` also in scope. A
    * global `implicit def` is deliberately '''not''' provided: it would silently override
    * the connector's default for every type that has a `RowMapper`, and — because the
    * mapper must be re-derivable on the executor (see below) — it could not be made both
    * seamless and serializable.
    *
    * ==Pass the derivation by name==
    *
    * `mapper` is by-name: pass the derivation itself — `RowMapper.of[T]`,
    * `RowMapper.cached[T]()`, or a stable companion/object `val` such as `Hotel.rowMapper`
    * — never a local val captured from an enclosing scope. The connector serializes the
    * scan RDD to executors, and a derived mapper closes over non-serializable driver
    * codecs; the by-name expression is re-evaluated on the executor so the mapper is
    * rebuilt there rather than shipped. See
    * [[net.nmoncho.helenus.spark.rdd.HelenusRowReaderFactory]] for the full rationale.
    */
  def helenusRowReaderFactory[T](
      mapper: => RowMapper[T]
  )(implicit ct: ClassTag[T]): RowReaderFactory[T] =
    HelenusRowReaderFactory[T](mapper)

  /** Adds the CQL-first `foreachPartitionCql` sink to any `RDD`. */
  implicit final class CqlSinkOps[In](private val rdd: RDD[In]) extends AnyVal {

    /** Writes every record through a Helenus [[ScalaPreparedStatement]], prepared once per
      * partition inside the connector's session, with compile-time bind-arity safety.
      *
      * This is the primary typed write path, for what `saveToCassandra` cannot express —
      * LWT / conditional writes (`IF NOT EXISTS`, `IF ...`), custom-`WHERE` updates and
      * deletes, and arbitrary CQL. Because the RDD element type must equal the statement's
      * `In`, either map to the bind tuple first and use a multi-arg `.prepare[...]`, or use
      * `.prepareFrom[T]` for a domain object:
      *
      * {{{
      * import net.nmoncho.helenus._
      * import net.nmoncho.helenus.spark._
      * import net.nmoncho.helenus.spark.sink.CassandraSink
      *
      * hotels
      *   .map(h => (h.id, h.name, h.phone, h.address, h.pois))
      *   .foreachPartitionCql(
      *     "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS"
      *       .toCQL(_)
      *       .prepare[String, String, String, Address, Set[String]],
      *     CassandraSink.Config()
      *   )
      * }}}
      *
      * Pass the statement builder as a lambda so it is prepared on the executor; only the
      * small function crosses the wire (its implicit codecs are re-resolved there). The
      * connector's session is used, configured by the `spark.cassandra.*` keys.
      */
    def foreachPartitionCql[Out](
        builder: CqlSession => ScalaPreparedStatement[In, Out],
        config: CassandraSink.Config = CassandraSink.Config()
    ): Unit =
      CassandraSink.write(rdd, builder, config)
  }

  /** Adds the CQL-first `foreachPartitionCql` sink to any `Dataset`.
    *
    * A thin forward to the [[CqlSinkOps]] `RDD` sink via `Dataset.rdd`, so a typed dataset
    * can be written through a Helenus prepared statement without dropping to `.rdd` by hand.
    * Typed Catalyst encoders and a Helenus DataSource are explicitly out of scope — the
    * structured (DataFrame / Dataset) read and write path stays the connector's format.
    */
  implicit final class CqlDatasetSinkOps[In](private val ds: Dataset[In]) extends AnyVal {

    /** As [[CqlSinkOps.foreachPartitionCql]], but sourced from a `Dataset[In]`.
      *
      * Because the element type must equal the statement's `In`, use `.prepareFrom[T]` for a
      * domain object (its `Mapping[T]` binds the record) or map to the bind tuple first and
      * use a multi-arg `.prepare[...]`:
      *
      * {{{
      * import net.nmoncho.helenus._
      * import net.nmoncho.helenus.spark._
      * import net.nmoncho.helenus.spark.sink.CassandraSink
      *
      * implicit val hotelMapping: Mapping[Hotel] = Mapping[Hotel]()
      *
      * val ds: org.apache.spark.sql.Dataset[Hotel] = // ...
      * ds.foreachPartitionCql(
      *   "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
      *     .toCQL(_)
      *     .prepareFrom[Hotel],
      *   CassandraSink.Config()
      * )
      * }}}
      */
    def foreachPartitionCql[Out](
        builder: CqlSession => ScalaPreparedStatement[In, Out],
        config: CassandraSink.Config = CassandraSink.Config()
    ): Unit =
      CassandraSink.write(ds.rdd, builder, config)
  }

}
