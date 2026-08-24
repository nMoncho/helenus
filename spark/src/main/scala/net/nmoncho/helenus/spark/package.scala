/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import scala.reflect.ClassTag

import com.datastax.spark.connector.rdd.reader.RowReaderFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.spark.rdd.HelenusRowReaderFactory

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

}
