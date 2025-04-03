/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink
package source

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util
import java.util.concurrent.Callable

import scala.collection.immutable.Queue
import scala.jdk.CollectionConverters._
import scala.util.Using

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner
import net.nmoncho.helenus.flink.source.CassandraSplit.Generator
import net.nmoncho.helenus.flink.writeBigInt
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.flink.api.connector.source.SplitEnumerator
import org.apache.flink.api.connector.source.SplitEnumeratorContext
import org.apache.flink.core.io.SimpleVersionedSerializer
import org.slf4j.LoggerFactory

case class CassandraEnumeratorState(
    numSplitsLeftToGenerate: Long,
    increment: BigInt,
    startToken: BigInt,
    maxToken: BigInt,
    splitsToReassign: Queue[CassandraSplit]
) {

  def addSplitsBack(elems: util.List[CassandraSplit]): CassandraEnumeratorState =
    copy(
      splitsToReassign = this.splitsToReassign ++ elems.asScala
    )

  def nextSplit(): (Option[CassandraSplit], CassandraEnumeratorState) =
    splitsToReassign.dequeueOption match {
      case Some((split, remaining)) =>
        Some(split) -> copy(splitsToReassign = remaining)

      case _ if numSplitsLeftToGenerate != 0L =>
        val endToken = if (numSplitsLeftToGenerate == 1L) maxToken else startToken + increment
        val split    = CassandraSplit(startToken, endToken)

        Some(split) -> copy(
          startToken              = endToken,
          numSplitsLeftToGenerate = numSplitsLeftToGenerate - 1
        )

      case _ =>
        None -> this
    }
}

object CassandraEnumeratorState {

  private val log = LoggerFactory.getLogger(classOf[CassandraEnumeratorState])

  def empty(): CassandraEnumeratorState =
    CassandraEnumeratorState(0, 0, 0, 0, Queue.empty)

  val SerializerVersion: Int = 0

  val Serializer: SimpleVersionedSerializer[CassandraEnumeratorState] with Serializable =
    new SimpleVersionedSerializer[CassandraEnumeratorState] with Serializable {
      override val getVersion: Int = SerializerVersion

      override def serialize(state: CassandraEnumeratorState): Array[Byte] = {
        val baos = new ByteArrayOutputStream()

        Using(new ObjectOutputStream(baos)) { oos =>
          oos.writeInt(state.splitsToReassign.size)
          for (split <- state.splitsToReassign) {
            val serializedSplit = CassandraSplit.serializer.serialize(split)

            oos.writeInt(serializedSplit.length)
            oos.write(serializedSplit)
          }

          oos.writeLong(state.numSplitsLeftToGenerate)
          writeBigInt(state.increment, oos)
          writeBigInt(state.startToken, oos)
          writeBigInt(state.maxToken, oos)

        }.map(_ => baos.toByteArray).get
      }

      override def deserialize(version: Int, serialized: Array[Byte]): CassandraEnumeratorState =
        Using.Manager { use =>
          // Don't remove me 'import scala.collection.compat._'
          import scala.collection.compat._ // scalafix:ok

          val bais = use(new ByteArrayInputStream(serialized))
          val ois  = use(new ObjectInputStream(bais))

          val splitsToReassign     = new util.ArrayDeque[CassandraSplit]()
          val splitsToReassignSize = ois.readInt()

          (0 until splitsToReassignSize).foreach { _ =>
            val splitSize = ois.readInt()
            val bytes     = new Array[Byte](splitSize)
            ois.readFully(bytes)

            val split =
              CassandraSplit.serializer.deserialize(CassandraSplit.serializerVersion, bytes)
            splitsToReassign.add(split)
          }

          CassandraEnumeratorState(
            numSplitsLeftToGenerate = ois.readLong(),
            increment               = readBigInt(ois),
            startToken              = readBigInt(ois),
            maxToken                = readBigInt(ois),
            splitsToReassign        = splitsToReassign.asScala.to(Queue)
          )
        }.get
    }

  def splitEnumerator(
      enumeratorContext: SplitEnumeratorContext[CassandraSplit],
      enumeratorCheckpoint: Option[CassandraEnumeratorState],
      maxSplitMemorySize: Long,
      bstmt: CqlSession => ScalaBoundStatement[_],
      config: CassandraSource.Config
  ): SplitEnumerator[CassandraSplit, CassandraEnumeratorState] =
    new SplitEnumerator[CassandraSplit, CassandraEnumeratorState] {
      private var state = enumeratorCheckpoint.getOrElse(CassandraEnumeratorState.empty())

      private val session = config.session()

      override def start(): Unit =
        enumeratorContext.callAsync(
          new Callable[CassandraEnumeratorState] {
            override def call(): CassandraEnumeratorState = prepareSplits()
          },
          (preparedState: CassandraEnumeratorState, throwable: Throwable) => {
            if (throwable != null) {
              log.error("Failed to prepare splits", throwable)
              // throw exception?
            } else {
              log.debug("Initialized CassandraEnumeratorState: {}", preparedState)
              state = preparedState
            }
          }
        )

      private def prepareSplits(): CassandraEnumeratorState = {
        val parallelism = enumeratorContext.currentParallelism()
        val partitioner = CassandraPartitioner(session)
        val (keyspace, table) =
          extractKeyspaceTable(session, bstmt(session).getPreparedStatement.getQuery)

        val generator =
          new Generator(partitioner, session, keyspace, table, parallelism, maxSplitMemorySize)

        generator.prepareSplit()
      }

      override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = {
        checkReaderRegistered(subtaskId)
        val (splitOpt, nextState) = state.nextSplit()

        splitOpt match {
          case Some(split) =>
            log.info("Assigning splits to reader {}", subtaskId)
            enumeratorContext.assignSplit(split, subtaskId)

          case None =>
            log.info(
              "No split assigned to reader {} because the enumerator has no unassigned split left. Sending NoMoreSplitsEvent to reader",
              subtaskId
            )
            enumeratorContext.signalNoMoreSplits(subtaskId)
        }

        state = nextState
      }

      private def checkReaderRegistered(readerId: Int): Unit =
        if (!enumeratorContext.registeredReaders().containsKey(readerId)) {
          throw new IllegalStateException(
            s"Reader $readerId is not registered to source coordinator"
          )
        }

      override def addSplitsBack(splits: util.List[CassandraSplit], subtaskId: Int): Unit =
        // splits that were assigned to a failed reader and that were not part of a checkpoint, so
        // after restoration, they need to be reassigned
        state.addSplitsBack(splits)

      // nothing to do on reader registration as the CassandraSplits are generated lazily
      override def addReader(subtaskId: Int): Unit = ()

      override def snapshotState(checkpointId: Long): CassandraEnumeratorState =
        state

      override def close(): Unit =
        try {
          session.close()
        } catch {
          case t: Throwable =>
            log.error("Error while closing session.", t)
        }
    }
}
