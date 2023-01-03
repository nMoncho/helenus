/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import _root_.akka.Done
import _root_.akka.NotUsed
import _root_.akka.stream.alpakka.cassandra.CassandraWriteSettings
import _root_.akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import _root_.akka.stream.scaladsl._
import _root_.net.nmoncho.helenus.internal.cql.ScalaPreparedStatement
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BatchStatement

package object akka {

  implicit def toExtension(implicit cassandraSession: CassandraSession): CqlSessionExtension =
    new CqlSessionExtension {
      override lazy val session: CqlSession = Await.result(
        cassandraSession.underlying(),
        Duration.Inf // FIXME can we configure this?
      )
    }

  implicit class ScalaPreparedStatementAkkaReadSyncOps[U, T](
      private val pstmt: ScalaPreparedStatement[U, T]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      *
      * @param u query parameters
      */
    def asReadSource(u: U)(implicit session: CassandraSession): Source[T, NotUsed] =
      Source
        .future(session.underlying())
        .flatMapConcat { cqlSession =>
          implicit val session: CqlSessionExtension = cqlSession.toScala

          Source.fromPublisher(pstmt.executeReactive(u))
        }

  }

  implicit class ScalaPreparedStatementAkkaReadAsyncOps[U, T](
      private val pstmt: Future[ScalaPreparedStatement[U, T]]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      *
      * @param u query parameters
      */
    def asReadSource(u: U)(
        implicit session: CassandraSession,
        ec: ExecutionContext
    ): Source[T, NotUsed] = {
      Source.futureSource(
        pstmt.map(
          _.asReadSource(u)
        )
      )
    }.mapMaterializedValue(_ => NotUsed)

  }

  implicit class ScalaPreparedStatementAkkaWriteOps[U, T](
      private val pstmt: ScalaPreparedStatement[U, T]
  ) extends AnyVal {

    /** A `Flow` writing to Cassandra for every stream element.
      * The element to be persisted is emitted unchanged.
      *
      * @param writeSettings   settings to configure the write operation
      * @param session         implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteFlow(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession): Flow[U, U, NotUsed] =
      Flow
        .lazyFlow { () =>
          Flow[U]
            .mapAsync(writeSettings.parallelism) { element =>
              session
                .executeWrite(pstmt(element))
                .map(_ => element)(ExecutionContext.parasitic)
            }
        }
        .mapMaterializedValue(_ => NotUsed)

    def asWriteFlowWithContext[Ctx](
        writeSettings: CassandraWriteSettings
    )(
        implicit session: CassandraSession
    ): FlowWithContext[U, Ctx, U, Ctx, NotUsed] =
      FlowWithContext.fromTuples {
        Flow
          .lazyFlow { () =>
            Flow[(U, Ctx)].mapAsync(writeSettings.parallelism) { case tuple @ (element, _) =>
              session
                .executeWrite(pstmt(element))
                .map(_ => tuple)(ExecutionContext.parasitic)
            }
          }
          .mapMaterializedValue(_ => NotUsed)
      }

    /** Creates a `Flow` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings   settings to configure the batching and the write operation
      * @param groupingKey     groups the elements to go into the same batch
      * @param session         implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteFlowBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession): Flow[U, U, NotUsed] = {
      import scala.jdk.CollectionConverters._

      Flow
        .lazyFlow { () =>
          Flow[U]
            .groupedWithin(writeSettings.maxBatchSize, writeSettings.maxBatchWait)
            .map(_.groupBy(groupingKey).values.toList)
            .mapConcat(identity)
            .mapAsyncUnordered(writeSettings.parallelism) { list =>
              val boundStatements = list.map(pstmt.apply)
              val batchStatement =
                BatchStatement.newInstance(writeSettings.batchType).addAll(boundStatements.asJava)
              session.executeWriteBatch(batchStatement).map(_ => list)(ExecutionContext.parasitic)
            }
            .mapConcat(_.toList)
        }
        .mapMaterializedValue(_ => NotUsed)
    }

    /** A `Sink` writing to Cassandra for every stream element.
      *
      * Unlike [[asWriteFlow]], stream elements are ignored after being persisted.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteSink(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession): Sink[U, Future[Done]] =
      asWriteFlow(writeSettings)
        .toMat(Sink.ignore)(Keep.right)

    /** Creates a `Sink` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteSinkBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession): Sink[U, Future[Done]] =
      asWriteFlowBatched(writeSettings, groupingKey)
        .toMat(Sink.ignore)(Keep.right)
  }

  implicit class AsyncScalaPreparedStatementAkkaWriteOps[U, T](
      private val futurePstmt: Future[ScalaPreparedStatement[U, T]]
  ) extends AnyVal {

    /** A `Flow` writing to Cassandra for every stream element.
      * The element to be persisted is emitted unchanged.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteFlow(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession, ec: ExecutionContext): Flow[U, U, NotUsed] =
      Flow
        .lazyFlow { () =>
          Flow[U]
            .mapAsync(writeSettings.parallelism) { element =>
              for {
                pstmt <- futurePstmt
                _ <- session.executeWrite(pstmt(element))
              } yield element
            }
        }
        .mapMaterializedValue(_ => NotUsed)

    def asWriteFlowWithContext[Ctx](
        writeSettings: CassandraWriteSettings
    )(
        implicit session: CassandraSession,
        ec: ExecutionContext
    ): FlowWithContext[U, Ctx, U, Ctx, NotUsed] =
      FlowWithContext.fromTuples {
        Flow
          .lazyFlow { () =>
            Flow[(U, Ctx)].mapAsync(writeSettings.parallelism) { case tuple @ (element, _) =>
              for {
                pstmt <- futurePstmt
                _ <- session.executeWrite(pstmt(element))
              } yield tuple
            }
          }
          .mapMaterializedValue(_ => NotUsed)
      }

    /** Creates a `Flow` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteFlowBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession, ec: ExecutionContext): Flow[U, U, NotUsed] = {
      import scala.jdk.CollectionConverters._

      Flow
        .lazyFlow { () =>
          Flow[U]
            .groupedWithin(writeSettings.maxBatchSize, writeSettings.maxBatchWait)
            .map(_.groupBy(groupingKey).values.toList)
            .mapConcat(identity)
            .mapAsyncUnordered(writeSettings.parallelism) { list =>
              for {
                boundStatements <- Future.traverse(list)(element =>
                  futurePstmt.map(_.apply(element))
                )
                batchStatement =
                  BatchStatement.newInstance(writeSettings.batchType).addAll(boundStatements.asJava)
                execution <- session.executeWriteBatch(batchStatement).map(_ => list)(ec)
              } yield execution
            }
            .mapConcat(_.toList)
        }
        .mapMaterializedValue(_ => NotUsed)
    }

    /** A `Sink` writing to Cassandra for every stream element.
      *
      * Unlike [[asWriteFlow]], stream elements are ignored after being persisted.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteSink(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession, ec: ExecutionContext): Sink[U, Future[Done]] =
      asWriteFlow(writeSettings)
        .toMat(Sink.ignore)(Keep.right)

    /** Creates a `Sink` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteSinkBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession, ec: ExecutionContext): Sink[U, Future[Done]] =
      asWriteFlowBatched(writeSettings, groupingKey)
        .toMat(Sink.ignore)(Keep.right)
  }

}
