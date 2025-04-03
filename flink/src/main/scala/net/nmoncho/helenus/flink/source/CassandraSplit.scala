/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink.source

import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import scala.collection.immutable.Queue
import scala.jdk.OptionConverters.RichOptional
import scala.util.Using

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.TokenMap
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenFactory
import com.datastax.oss.driver.internal.core.metadata.token.RandomTokenFactory
import com.datastax.oss.driver.internal.core.metadata.token.TokenFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.flink.readBigInt
import net.nmoncho.helenus.flink.writeBigInt
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.flink.api.connector.source.SourceSplit
import org.apache.flink.core.io.SimpleVersionedSerializer
import org.slf4j.LoggerFactory

/** Immutable [[SourceSplit]] for Cassandra source. A Cassandra split is a slice of the Cassandra
  * tokens ring (i.e. a ringRange).
  */
case class CassandraSplit(ringRangeStart: BigInt, ringRangeEnd: BigInt) extends SourceSplit {

  override def splitId(): String =
    s"(${ringRangeStart.toString()},${ringRangeEnd.toString()})"

}

object CassandraSplit {

  private val log = LoggerFactory.getLogger(classOf[CassandraSplit])

  val serializerVersion: Int = 0

  val serializer: SimpleVersionedSerializer[CassandraSplit] with Serializable =
    new SimpleVersionedSerializer[CassandraSplit] with Serializable {
      val currentVersion: Int = serializerVersion

      override val getVersion: Int = currentVersion

      override def serialize(split: CassandraSplit): Array[Byte] = {
        val baos = new ByteArrayOutputStream()

        Using(new ObjectOutputStream(baos)) { oos =>
          writeBigInt(split.ringRangeStart, oos)
          writeBigInt(split.ringRangeStart, oos)

        }.map(_ => baos.toByteArray).get
      }

      override def deserialize(version: Int, serialized: Array[Byte]): CassandraSplit =
        Using.Manager { use =>
          val bais = use(new ByteArrayInputStream(serialized))
          val ois  = use(new ObjectInputStream(bais))

          CassandraSplit(
            ringRangeStart = readBigInt(ois),
            ringRangeEnd   = readBigInt(ois)
          )
        }.get
    }

  class Generator(
      partitioner: CassandraPartitioner,
      session: CqlSession,
      keyspace: String,
      table: String,
      parallelism: Int,
      maxSplitMemorySize: Long
  ) {

    /** Prepare the [[CassandraEnumeratorState]] for lazy generation of [[CassandraSplit]]s:
      * calculate `numSplitsToGenerate` based on estimated target table size and provided
      * `maxSplitMemorySize` and calculate `increment` which is the size of a split in
      * tokens.
      */
    def prepareSplit(): CassandraEnumeratorState = {
      val splitsToGenerate = decideOnNumSplits()

      CassandraEnumeratorState(
        numSplitsLeftToGenerate = splitsToGenerate,
        increment               = partitioner.ringSize / splitsToGenerate,
        startToken              = partitioner.minToken,
        maxToken                = partitioner.maxToken,
        splitsToReassign        = Queue.empty
      )
    }

    private[source] def prepareSplit(splitsToGenerate: Long): CassandraEnumeratorState =
      CassandraEnumeratorState(
        numSplitsLeftToGenerate = splitsToGenerate,
        increment               = partitioner.ringSize / splitsToGenerate,
        startToken              = partitioner.minToken,
        maxToken                = partitioner.maxToken,
        splitsToReassign        = Queue.empty
      )

    /** Determine `numSplits` based on the estimation of the target table size and configured
      * `maxSplitMemorySize`. Provide fallbacks when table size is unavailable, too few splits
      * are calculated.
      */
    def decideOnNumSplits(): Long =
      decideOnNumSplits(estimateTableSize())

    private[source] def decideOnNumSplits(estimatedTableSize: Long): Long =
      if (estimatedTableSize == 0) {
        log.info(
          "Cassandra size estimates are not available for {}.{} table. Creating as many splits as parallelism ({})",
          keyspace,
          table,
          parallelism.toString
        )

        parallelism
      } else {
        log.debug(
          "Estimated size for {}.{} table is {} bytes",
          keyspace,
          table,
          estimatedTableSize.toString
        )

        // Create estimateTableSize / maxSplitMemorySize splits.
        // If that makes too few splits, use `parallelism`
        val splits =
          if (estimatedTableSize / maxSplitMemorySize == 0) parallelism
          else estimatedTableSize / maxSplitMemorySize

        log.info(
          "maxSplitMemorySize set value ({}) leads to the creation of {} splits",
          maxSplitMemorySize.toString,
          splits.toString: Any
        )

        splits
      }

    /** Estimates the size of the table in bytes. Cassandra size estimates can be 0 if the data was
      * just inserted and the amount of data in the table was small. This is very common situation
      * during tests.
      */
    def estimateTableSize(): Long =
      estimateTableSize(tokenRangesOfTable())

    private[source] def estimateTableSize(ranges: List[TokenRange]): Long = {
      val size = ranges.foldLeft(0L)((acc, tokenRange) =>
        acc + tokenRange.meanPartitionSize * tokenRange.partitionCount
      )
      val fraction = ringFraction(ranges)

      // ringFraction can be null if the size estimates are not available
      if (fraction != 0) Math.round(size / fraction)
      else 0L
    }

    /** Gets the list of token ranges that the table occupies on a given Cassandra node. */
    private def tokenRangesOfTable(): List[TokenRange] = {
      // Don't remove me 'import scala.collection.compat._'
      import scala.collection.compat._ // scalafix:ok

      """SELECT range_start, range_end, partitions_count, mean_partition_size
        |FROM system.size_estimates
        |WHERE keyspace_name = ? AND table_name = ?""".stripMargin
        .toCQL(session)
        .prepare[String, String]
        .as[TokenRange]
        .execute(keyspace, table)(session)
        .to(List)
    }

    /** The values that we get from system.size_estimates are for one node. We need to extrapolate to
      * the whole cluster. This method estimates the percentage, the node represents in the cluster.
      *
      * @param ranges The list of [[TokenRange]] to estimate
      * @return The percentage the node represent in the whole cluster
      */
    private[source] def ringFraction(ranges: List[TokenRange]): Float = { // TODO Why float?
      val addressedTokens = ranges.foldLeft(BigInt(0)) { (acc, tokenRange) =>
        acc + distance(tokenRange.rangeStart, tokenRange.rangeEnd)
      }

      (BigDecimal(addressedTokens) / BigDecimal(partitioner.ringSize)).floatValue
    }

    /** Measure distance between two tokens.
      *
      * @param token1 The measure is symmetrical so token1 and token2 can be exchanged
      * @param token2 The measure is symmetrical so token1 and token2 can be exchanged
      * @return Number of tokens that separate token1 and token2
      */
    private def distance(token1: BigInt, token2: BigInt): BigInt =
      if (token2 > token1) token2 - token1
      else token2 - token1 + partitioner.ringSize
  }

  sealed abstract class CassandraPartitioner(
      val className: String,
      val minToken: BigInt,
      val maxToken: BigInt
  ) {
    val ringSize: BigInt = maxToken - minToken + 1

    def factory: TokenFactory
  }

  object CassandraPartitioner {

    def apply(session: CqlSession): CassandraPartitioner =
      CassandraPartitioner(session.getMetadata.getTokenMap.toScala, session)

    def apply(tokenMap: Option[TokenMap], session: CqlSession): CassandraPartitioner = {
      val name = tokenMap
        .map(_.getPartitionerName)
        .orElse {
          log.debug("fetching partitioner from `system.local` table")
          session.execute("SELECT partitioner FROM system.local").nextOption().map(_.getString(0))
        }
        .getOrElse(throw new IllegalStateException("Couldn't retrieve Partitioner"))

      if (name.contains(Murmur3Partitioner.className)) Murmur3Partitioner
      else RandomPartitioner
    }

    def token(session: CqlSession, value: BigInt): Token =
      session.getMetadata.getTokenMap.toScala.map(_.parse(value.toString())).getOrElse {
        val partitioner = CassandraPartitioner(session)

        partitioner.factory.parse(value.toString())
      }

    case object Murmur3Partitioner
        extends CassandraPartitioner(
          "Murmur3Partitioner",
          -BigInt(2).pow(63),
          BigInt(2).pow(63) - 1
        ) {
      override def factory: TokenFactory = new Murmur3TokenFactory()
    }

    case object RandomPartitioner
        extends CassandraPartitioner(
          "RandomPartitioner",
          BigInt(0),
          BigInt(2).pow(127) - 1
        ) {
      override def factory: TokenFactory = new RandomTokenFactory()
    }
  }

  case class TokenRange(
      partitionCount: Long,
      meanPartitionSize: Long,
      rangeStart: BigInt,
      rangeEnd: BigInt
  )

  object TokenRange {
    // $COVERAGE-OFF$
    // Given this is read from `system.size_estimates` which is empty at test time, we cannot test it
    implicit val mapper: RowMapper[TokenRange] = new RowMapper[TokenRange] {
      override def apply(row: Row): TokenRange = TokenRange(
        partitionCount    = row.getCol[Long]("partitions_count"),
        meanPartitionSize = row.getCol[Long]("mean_partition_size"),
        rangeStart        = row.getCol[Long]("range_start"),
        rangeEnd          = row.getCol[Long]("range_end")
      )
    }
    // $COVERAGE-ON$
  }
}
