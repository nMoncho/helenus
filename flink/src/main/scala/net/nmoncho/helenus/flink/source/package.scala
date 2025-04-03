/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink

import scala.util.matching.Regex

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.api.RowMapper
import org.apache.flink.api.common.io._
import org.apache.flink.api.common.io.statistics.BaseStatistics
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.source._
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.configuration.Configuration
import org.apache.flink.configuration.MemorySize
import org.apache.flink.core.io._

package object source {

  val MinSplitMemorySize: Long        = MemorySize.ofMebiBytes(10).getBytes
  val MaxSplitMemorySizeDefault: Long = MemorySize.ofMebiBytes(64).getBytes

  val SelectRegex: Regex =
    "(?i)SELECT\\s+(.+?)\\s+FROM\\s+([a-zA-Z0-9_\"]+?\\.)?([a-zA-Z0-9_\"]+).*$".r

  /** Extracts a query's `keyspace` and `table`.
    *
    * If the query doesn't specify the keypsace, the session's keyspace will used instead.
    */
  def extractKeyspaceTable(session: CqlSession, query: String): (String, String) = query match {
    case SelectRegex(_, keyspace, table) =>
      Option(keyspace)
        .map(s => s.substring(0, s.length - 1))
        .getOrElse(session.getKeyspace.get().toString) -> table

    case _ =>
      throw new IllegalArgumentException("Invalid CQL query not matching existing regex")
  }

  /** Turns this [[ScalaBoundStatement]] builder into a Flink [[InputFormat]]
    *
    * @param config
    * @param mapper how to map a Cassandra Row into an ouput
    */
  def asInputFormat[Out](
      bstmtBuilder: CqlSession => ScalaBoundStatement[Out],
      config: CassandraSource.Config
  )(implicit mapper: RowMapper[Out]): RichInputFormat[Out, InputSplit] with NonParallelInput =
    new RichInputFormat[Out, InputSplit] with NonParallelInput {

      private var session: CqlSession     = _
      private var iterator: Iterator[Out] = _

      override def nextRecord(reuse: Out): Out =
        iterator.next()

      override def reachedEnd(): Boolean =
        !iterator.hasNext

      override def createInputSplits(minNumSplits: Int): Array[InputSplit] =
        Array(new GenericInputSplit(0, 1))

      override def getInputSplitAssigner(inputSplits: Array[InputSplit]): InputSplitAssigner =
        new DefaultInputSplitAssigner(inputSplits)

      override def getStatistics(cachedStatistics: BaseStatistics): BaseStatistics =
        cachedStatistics

      override def configure(parameters: Configuration): Unit = ()

      override def open(split: InputSplit): Unit = {
        session  = config.session()
        iterator = bstmtBuilder(session).execute()(session, mapper).iter
      }

      override def close(): Unit =
        session.close()
    }

  /** Turns this [[ScalaBoundStatement]] builder into a Flink [[Source]]
    *
    * This uses a [[MaxSplitMemorySizeDefault]] for how big a split is allowed to bex
    *
    * @param config
    */
  def asSource[Out: TypeInformation: RowMapper](
      bstmt: CqlSession => ScalaBoundStatement[Out],
      config: CassandraSource.Config
  ): Source[Out, CassandraSplit, CassandraEnumeratorState] =
    asSource[Out](bstmt, MaxSplitMemorySizeDefault, config)

  /** Turns this [[ScalaBoundStatement]] builder into a Flink [[Source]]
    *
    * @param config
    * @param maxSplitMemorySize how much memory should be every split
    */
  def asSource[Out: TypeInformation: RowMapper](
      bstmt: CqlSession => ScalaBoundStatement[Out],
      maxSplitMemorySize: Long,
      config: CassandraSource.Config
  ): Source[Out, CassandraSplit, CassandraEnumeratorState] =
    new Source[Out, CassandraSplit, CassandraEnumeratorState] with ResultTypeQueryable[Out] {
      require(
        maxSplitMemorySize >= MinSplitMemorySize,
        s"Defined maxSplitMemorySize $maxSplitMemorySize is below minimum $MinSplitMemorySize"
      )

      override val getBoundedness: Boundedness =
        Boundedness.BOUNDED

      override def createEnumerator(
          enumContext: SplitEnumeratorContext[CassandraSplit]
      ): SplitEnumerator[CassandraSplit, CassandraEnumeratorState] =
        CassandraEnumeratorState.splitEnumerator(
          enumContext,
          None,
          maxSplitMemorySize,
          bstmt,
          config
        )

      override def restoreEnumerator(
          enumContext: SplitEnumeratorContext[CassandraSplit],
          checkpoint: CassandraEnumeratorState
      ): SplitEnumerator[CassandraSplit, CassandraEnumeratorState] =
        CassandraEnumeratorState.splitEnumerator(
          enumContext,
          Some(checkpoint),
          maxSplitMemorySize,
          bstmt,
          config
        )

      override def createReader(
          readerContext: SourceReaderContext
      ): SourceReader[Out, CassandraSplit] =
        new CassandraSourceReader[Out](readerContext, config.session(), bstmt)

      override val getSplitSerializer: SimpleVersionedSerializer[CassandraSplit] =
        CassandraSplit.serializer

      override val getEnumeratorCheckpointSerializer
          : SimpleVersionedSerializer[CassandraEnumeratorState] =
        CassandraEnumeratorState.Serializer

      override val getProducedType: TypeInformation[Out] =
        implicitly[TypeInformation[Out]]
    }
}
