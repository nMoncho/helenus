/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.source

import java.util
import java.util.concurrent.atomic.AtomicBoolean

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.ExecutionInfo
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.token.Token
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.flink.source.CassandraSourceReader._
import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner
import org.apache.flink.api.connector.source.SourceOutput
import org.apache.flink.api.connector.source.SourceReaderContext
import org.apache.flink.connector.base.source.reader._
import org.apache.flink.connector.base.source.reader.splitreader.SplitReader
import org.apache.flink.connector.base.source.reader.splitreader.SplitsChange
import org.slf4j.LoggerFactory

class CassandraSourceReader[Out: RowMapper](
    context: SourceReaderContext,
    session: CqlSession,
    bstmt: CqlSession => ScalaBoundStatement[Out]
) extends ReaderBase[Out](
      () => splitReader(session, bstmt),
      recordEmitter[Out](),
      context.getConfiguration,
      context
    ) {

  override def start(): Unit =
    context.sendSplitRequest()

  override def onSplitFinished(finishedSplitIds: util.Map[String, CassandraSplit]): Unit =
    context.sendSplitRequest()

  override def initializedState(split: CassandraSplit): CassandraSplit =
    split

  override def toSplitType(splitId: String, split: CassandraSplit): CassandraSplit =
    split

  override def close(): Unit = {
    super.close()

    try {
      session.close()
    } catch {
      case t: Throwable =>
        log.error("Error while closing session", t)
    }
  }
}

object CassandraSourceReader {

  type CassandraRow    = (Row, ExecutionInfo)
  type ReaderBase[Out] =
    SingleThreadMultiplexSourceReaderBase[CassandraRow, Out, CassandraSplit, CassandraSplit]

  private val log = LoggerFactory.getLogger(classOf[CassandraSourceReader[_]])

  def recordEmitter[Out]()(
      implicit mapper: RowMapper[Out]
  ): RecordEmitter[CassandraRow, Out, CassandraSplit] =
    new RecordEmitter[CassandraRow, Out, CassandraSplit] {
      override def emitRecord(
          element: CassandraRow,
          output: SourceOutput[Out],
          splitState: CassandraSplit
      ): Unit =
        output.collect(mapper.apply(element._1))
    }

  def splitReader[Out](
      session: CqlSession,
      bstmt: CqlSession => ScalaBoundStatement[Out]
  ): SplitReader[CassandraRow, CassandraSplit] =
    new SplitReader[CassandraRow, CassandraSplit] {

      private val unprocessedSplits = new util.HashSet[CassandraSplit]
      private val wakeup            = new AtomicBoolean(false)

      override def fetch(): RecordsWithSplitIds[CassandraRow] = {
        val recordsBySplit = new util.HashMap[String, util.Collection[CassandraRow]]()
        val finishedSplits = new util.HashSet[String]()

        val userQuery         = bstmt(session)
        val (keyspace, table) =
          extractKeyspaceTable(session, userQuery.getPreparedStatement.getQuery)
        val partitioningKey = fetchPartitioningKey(keyspace, table)

        // Set wakeup to false to start consuming
        wakeup.compareAndSet(true, false)
        Breaks.breakable {
          // allow to interrupt the reading of splits especially the blocking session.execute()
          // call as requested in the API
          if (wakeup.get()) {
            Breaks.break()
          }

          unprocessedSplits.asScala.foreach { split =>
            try {
              val startToken = CassandraPartitioner.token(session, split.ringRangeStart)
              val endToken   = CassandraPartitioner.token(session, split.ringRangeEnd)

              val stmt =
                generateFinalQuery(session, userQuery, partitioningKey, startToken, endToken)

              val rs = session.execute(stmt)

              // add all the records of the split to the output (in memory).
              // It is "safe" because each split has a configurable maximum memory size
              addRecordsToOutput(rs, split, recordsBySplit)
              // add the already read (or even empty) split to finished splits
              finishedSplits.add(split.splitId())
              // for reentrant calls: if fetch is restarted,
              // do not reprocess the already processed splits
              unprocessedSplits.remove(split)
            } catch {
              case t: Throwable =>
                log.error("Error while reading split ", t)
            }
          }
        }

        new RecordsBySplits(recordsBySplit, finishedSplits)
      }

      private[source] def fetchPartitioningKey(keyspace: String, table: String): String =
        session.getMetadata
          .getKeyspace(keyspace)
          .get()
          .getTable(table)
          .get()
          .getPartitionKey
          .asScala
          .map(_.getName)
          .mkString(",")

      /** This method populates the `output` map that is used to create the [[RecordsBySplits]]
        * that are output by the fetch method
        */
      private[source] def addRecordsToOutput(
          rs: ResultSet,
          split: CassandraSplit,
          output: util.Map[String, util.Collection[CassandraRow]]
      ): Unit =
        rs.forEach(row =>
          output
            .computeIfAbsent(split.splitId(), id => new util.ArrayList())
            .add(row -> rs.getExecutionInfo)
        )

      override def wakeUp(): Unit =
        wakeup.compareAndSet(false, true)

      override def handleSplitsChanges(splitsChanges: SplitsChange[CassandraSplit]): Unit =
        unprocessedSplits.addAll(splitsChanges.splits())

      // nothing to do as the cluster/session is managed by the SourceReader
      override def close(): Unit = ()
    }

  def generateFinalQuery(
      session: CqlSession,
      bstmt: ScalaBoundStatement[_],
      partitioningKey: String,
      startToken: Token,
      endToken: Token
  ): BoundStatement = {
    // FIXME this is ignoring all options in the bstmt, for example page size (maybe routing key option has to be ignored)
    val paramSize  = bstmt.getPreparedStatement.getVariableDefinitions.size()
    val query      = bstmt.getPreparedStatement.getQuery
    val rangeQuery = generateRangeQuery(query, partitioningKey)

    val stmt = session.prepare(rangeQuery).bind().setToken(0, startToken).setToken(1, endToken)

    (0 until paramSize).foldLeft(stmt) { (acc, idx) =>
      acc.setBytesUnsafe(idx + 2, bstmt.getBytesUnsafe(idx))
    }
  }

  def generateRangeQuery(query: String, partitioningKey: String): String = {
    val regexMatch = SelectRegex.findFirstMatchIn(query).getOrElse {
      throw new IllegalStateException(
        s"Failed to generate range query out of the provided query: $query"
      )
    }

    val whereIdx                 = query.toLowerCase.indexOf(" where ")
    val (insertionPoint, filter) = if (whereIdx != -1) {
      (whereIdx + " where ".length) -> s"(token($partitioningKey) >= ?) AND (token($partitioningKey) < ?) AND "
    } else {
      regexMatch.end(
        3
      ) -> s" WHERE (token($partitioningKey) >= ?) AND (token($partitioningKey) < ?)"
    }

    s"${query.substring(0, insertionPoint)}$filter${query.substring(insertionPoint)}"
  }
}
