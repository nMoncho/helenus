/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import net.nmoncho.helenus.api.tables.ddl.TableOptions._

/** The `WITH` options of a `CREATE TABLE`: everything that follows the column
  * list and `PRIMARY KEY`, other than `CLUSTERING ORDER BY` (which
  * [[CreateTable]] derives from the table's `CK` declaration). Every field is
  * optional, and only the ones that are set are rendered, in a fixed canonical
  * order, joined with ` AND ` (see [[render]]).
  *
  * These are not usually built by hand: use the fluent `with*` methods on
  * [[CreateTable]], e.g.
  * {{{
  * MyTable.create
  *   .withComment("audit log")
  *   .withGcGraceSeconds(864000)
  *   .withCompaction(Map("class" -> "LeveledCompactionStrategy"))
  *   .execute()
  * }}}
  *
  * For any option without a typed field (DSE-only options, or ones added by a
  * newer server), use [[CreateTable.withOption]] / [[extra]]: those are
  * rendered verbatim as `name = value`, so the caller supplies CQL-ready
  * syntax (a string value must already be quoted).
  *
  * @param comment                 human-readable table comment.
  * @param speculativeRetry        when to speculatively retry reads (e.g. `99percentile`, `50ms`, `NONE`).
  * @param additionalWritePolicy   speculative-retry policy for writes (Cassandra 4.0+).
  * @param gcGraceSeconds          seconds tombstones are kept before being eligible for GC.
  * @param bloomFilterFpChance     target false-positive chance for the SSTable bloom filters.
  * @param defaultTimeToLive       default TTL, in seconds, applied to inserted data (0 disables).
  * @param memtableFlushPeriodInMs forced memtable flush period, in milliseconds (0 disables).
  * @param minIndexInterval        minimum sampling interval for the partition index.
  * @param maxIndexInterval        maximum sampling interval for the partition index.
  * @param readRepair              read-repair behaviour: `BLOCKING` (default) or `NONE` (Cassandra 4.0+).
  * @param crcCheckChance          probability of checking SSTable checksums on read.
  * @param cdc                     whether change-data-capture is enabled for the table.
  * @param caching                 caching options, e.g. `Map("keys" -> "ALL", "rows_per_partition" -> "NONE")`.
  * @param compaction              compaction options, e.g. `Map("class" -> "SizeTieredCompactionStrategy")`.
  * @param compression             compression options, e.g. `Map("class" -> "LZ4Compressor")`.
  * @param memtable                named memtable configuration (Cassandra 4.1+).
  * @param id                      explicit table id (a UUID string), e.g. to recreate a dropped table (see <a href="https://cassandra.apache.org/doc/latest/cassandra/reference/cql-commands/create-table.html#restore-commit-log">restore-commit-log</a>).
  * @param extra                   escape hatch for any other option, rendered verbatim as `name = value`.
  */
final case class TableOptions(
    comment: Option[String]                         = None,
    speculativeRetry: Option[SpeculativeRetry]      = None,
    additionalWritePolicy: Option[SpeculativeRetry] = None,
    gcGraceSeconds: Option[Duration]                = None,
    bloomFilterFpChance: Option[Double]             = None,
    defaultTimeToLive: Option[Duration]             = None,
    memtableFlushPeriodInMs: Option[Duration]       = None,
    minIndexInterval: Option[Int]                   = None,
    maxIndexInterval: Option[Int]                   = None,
    readRepair: Option[ReadRepair]                  = None,
    crcCheckChance: Option[Double]                  = None,
    cdc: Option[Boolean]                            = None,
    caching: Option[Caching]                        = None,
    compaction: Option[CompactionStrategy]          = None,
    compression: Option[Compression]                = None,
    memtable: Option[String]                        = None,
    id: Option[String]                              = None,
    extra: Vector[(String, String)]                 = Vector.empty
) {

  import TableOptions._

  /** Whether no option is set (so no `WITH` clause is needed for options). */
  def isEmpty: Boolean = render.isEmpty

  /** The set options as rendered `name = value` fragments, in canonical order.
    * Empty when nothing is set. [[CreateTable]] joins these (after any
    * `CLUSTERING ORDER BY`) with ` AND ` into the final `WITH` clause.
    */
  def render: Seq[String] = {
    val buf = Vector.newBuilder[String]
    comment.foreach(v => buf += s"comment = ${quote(v)}")
    speculativeRetry.foreach(v => buf += s"speculative_retry = ${quote(v.value)}")
    additionalWritePolicy.foreach(v => buf += s"additional_write_policy = ${quote(v.value)}")
    gcGraceSeconds.foreach(v => buf += s"gc_grace_seconds = ${v.toSeconds}")
    bloomFilterFpChance.foreach(v => buf += s"bloom_filter_fp_chance = $v")
    defaultTimeToLive.foreach(v => buf += s"default_time_to_live = ${v.toSeconds}")
    memtableFlushPeriodInMs.foreach(v => buf += s"memtable_flush_period_in_ms = ${v.toMillis}")
    minIndexInterval.foreach(v => buf += s"min_index_interval = $v")
    maxIndexInterval.foreach(v => buf += s"max_index_interval = $v")
    readRepair.foreach(v => buf += s"read_repair = ${quote(v.value)}")
    crcCheckChance.foreach(v => buf += s"crc_check_chance = $v")
    cdc.foreach(v => buf += s"cdc = $v")
    if (caching.nonEmpty) buf += s"caching = ${renderMap(caching.get.cqlParams)}"
    if (compaction.nonEmpty) buf += s"compaction = ${renderMap(compaction.get.cqlParams)}"
    if (compression.nonEmpty) buf += s"compression = ${renderMap(compression.get.cqlParams)}"
    memtable.foreach(v => buf += s"memtable = ${quote(v)}")
    id.foreach(v => buf += s"id = ${quote(v)}")
    extra.foreach { case (name, value) => buf += s"$name = $value" }
    buf.result()
  }
}

object TableOptions {

  /** No options set. */
  val Empty: TableOptions = TableOptions()

  case class Caching(val keys: Caching.Keys, rowPerPartition: Caching.Rows) {
    def cqlParams: Map[String, String] =
      Map("keys" -> keys.value, "rows_per_partition" -> rowPerPartition.value)
  }
  object Caching {
    sealed abstract class Keys(val value: String)
    object Keys {
      case object None extends Keys("NONE")
      case object All extends Keys("ALL")
    }

    sealed abstract class Rows(val value: String)
    object Rows {
      case class Amount(n: Int) extends Rows(n.toString)
      case object None extends Rows("NONE")
      case object All extends Rows("ALL")
    }
  }

  sealed abstract class SpeculativeRetry(val value: String)
  object SpeculativeRetry {
    case object None extends SpeculativeRetry("NONE")
    case object Always extends SpeculativeRetry("ALWAYS")
    case class Percentile(percentile: Int) extends SpeculativeRetry(s"${percentile}PERCENTILE")
    case class Ms(millis: Int) extends SpeculativeRetry(s"${millis}MS")

    case class Min(percentile: Int, millis: Int)
        extends SpeculativeRetry(
          s"MIN(${Percentile(percentile).value},${Ms(millis).value})"
        )
    case class Max(percentile: Int, millis: Int)
        extends SpeculativeRetry(
          s"MAX(${Percentile(percentile).value},${Ms(millis).value})"
        )
  }

  sealed trait Compression { def cqlParams: Map[String, String] }
  object Compression {
    case object Disabled extends Compression {
      override val cqlParams: Map[String, String] = Map("enabled" -> "false")
    }

    case class LZ4Compressor(
        chunkLengthInKb: Int,
        compressorType: LZ4Compressor.CompressorType,
        compressionLevel: Int
    ) extends Compression {
      require(chunkLengthInKb > 0, "chunkLengthInKb must be greater than 0")
      require(
        compressionLevel >= 1 && compressionLevel <= 17,
        "compressionLevel must be between 1 and 17"
      )

      override def cqlParams: Map[String, String] = Map(
        "class" -> "LZ4Compressor",
        "chunk_length_in_kb" -> chunkLengthInKb.toString,
        "lz4_compressor_type" -> compressorType.value,
        "lz4_high_compressor_level" -> compressionLevel.toString
      )
    }

    object LZ4Compressor {
      sealed abstract class CompressorType(val value: String)
      object CompressorType {
        case object Fast extends CompressorType("fast")
        case object High extends CompressorType("high")
      }
    }

    case class ZstdCompressor(chunkLengthInKb: Int, compressionLevel: Int) extends Compression {
      require(chunkLengthInKb > 0, "chunkLengthInKb must be greater than 0")
      require(
        compressionLevel >= -131072 && compressionLevel <= 22,
        "compressionLevel must be between -131072 and 22"
      )
      override def cqlParams: Map[String, String] =
        Map(
          "class" -> "ZstdCompressor",
          "chunk_length_in_kb" -> chunkLengthInKb.toString,
          "compression_level" -> compressionLevel.toString
        )
    }

    case class SnappyCompressor(chunkLengthInKb: Int) extends Compression {
      require(chunkLengthInKb > 0, "chunkLengthInKb must be greater than 0")

      override def cqlParams: Map[String, String] =
        Map("class" -> "SnappyCompressor", "chunk_length_in_kb" -> chunkLengthInKb.toString)
    }

    case class DeflateCompressor(chunkLengthInKb: Int) extends Compression {
      require(chunkLengthInKb > 0, "chunkLengthInKb must be greater than 0")

      override def cqlParams: Map[String, String] =
        Map("class" -> "DeflateCompressor", "chunk_length_in_kb" -> chunkLengthInKb.toString)
    }
  }

  sealed trait CompactionStrategy { def cqlParams: Map[String, String] }
  object CompactionStrategy {

    sealed abstract class OverlapInclusionMethod(val value: String)
    object OverlapInclusionMethod {
      case object None extends OverlapInclusionMethod("NONE")
      case object Single extends OverlapInclusionMethod("SINGLE")
      case object Transitive extends OverlapInclusionMethod("TRANSITIVE")
    }

    sealed abstract class ByteUnit(val value: String)
    case object KiB extends ByteUnit("KiB")
    case object MiB extends ByteUnit("MiB")
    case object GiB extends ByteUnit("GiB")
    case object KB extends ByteUnit("KB")
    case object MB extends ByteUnit("MB")
    case object GB extends ByteUnit("GB")

    case class Bytes(amount: Int, unit: ByteUnit) {
      def toCQL: String = s"$amount${unit.value}"
    }

    case class UnifiedCompactionStrategy(
        enabled: Option[Boolean]                               = None,
        onlyPurgedRepairedTombstones: Option[Boolean]          = None,
        scalingParameters: Seq[Int]                            = Seq.empty,
        targetSSTableSize: Option[Bytes]                       = None,
        minSSTableSize: Option[Bytes]                          = None,
        baseShardCount: Option[Int]                            = None,
        ssTableGrowth: Option[Double]                          = None,
        expiredSSTableCheckFrequencySeconds: Option[Duration]  = None,
        maxSSTablesToCompact: Option[Int]                      = None,
        overlapInclusionMethod: Option[OverlapInclusionMethod] = None, // change to enum
        unsafeAggressiveSSTableExpiration: Option[Boolean]     = None
    ) extends CompactionStrategy {
      override def cqlParams: Map[String, String] = Seq(
        Some("class" -> "UnifiedCompactionStrategy"),
        enabled.map(b => "enabled" -> b.toString),
        onlyPurgedRepairedTombstones.map(b => "only_purge_repaired_tombstone" -> b.toString),
        scalingParameters.headOption.map(_ =>
          "scaling_parameters" -> scalingParameters.mkString("[", ",", "]")
        ),
        targetSSTableSize.map(s => "target_sstable_size" -> s.toCQL),
        minSSTableSize.map(s => "min_sstable_size" -> s.toCQL),
        baseShardCount.map(c => "base_shard_count" -> c.toString),
        ssTableGrowth.map(d => "sstable_growth" -> d.toString),
        expiredSSTableCheckFrequencySeconds.map(d =>
          "expired_sstable_check_frequency_seconds" -> d.getSeconds.toString
        ),
        maxSSTablesToCompact.map(c => "max_sstables_to_compact" -> c.toString),
        overlapInclusionMethod.map(m => "overlap_inclusion_method" -> m.value),
        unsafeAggressiveSSTableExpiration.map(b =>
          "unsafe_aggressive_sstable_expiration" -> b.toString
        )
      ).flatten.toMap
    }

    case class SizeTieredCompactionStrategy(
        enabled: Option[Boolean]                      = None,
        tombstoneCompactionInterval: Option[Duration] = None,
        tombstoneThreshold: Option[Duration]          = None,
        uncheckedTombstoneCompaction: Option[Boolean] = None,
        logAll: Option[Boolean]                       = None,
        minThreshold: Option[Int]                     = None,
        maxThreshold: Option[Int]                     = None,
        bucketLow: Option[Double]                     = None,
        bucketHigh: Option[Double]                    = None,
        minSSTableSize: Option[Bytes]                 = None,
        onlyPurgeRepairedTombstones: Option[Boolean]  = None
    ) extends CompactionStrategy {
      override def cqlParams: Map[String, String] = Seq(
        Some("class" -> "SizeTieredCompactionStrategy"),
        enabled.map(v => "enabled" -> v.toString),
        tombstoneCompactionInterval.map(v =>
          "tombstone_compaction_interval" -> v.toSeconds.toString
        ),
        tombstoneThreshold.map(v => "tombstone_threshold" -> v.toString),
        uncheckedTombstoneCompaction.map(v => "unchecked_tombstone_compaction" -> v.toString),
        logAll.map(v => "log_all" -> v.toString),
        minThreshold.map(v => "min_threshold" -> v.toString),
        maxThreshold.map(v => "max_threshold" -> v.toString),
        bucketLow.map(v => "bucket_low" -> v.toString),
        bucketHigh.map(v => "bucket_high" -> v.toString),
        minSSTableSize.map(v => "min_sstable_size" -> v.toCQL),
        onlyPurgeRepairedTombstones.map(v => "only_purge_repaired_tombstones" -> v.toString)
      ).flatten.toMap
    }

    case class LeveledCompactionStrategy(
        enabled: Option[Boolean]                      = None,
        tombstoneCompactionInterval: Option[Duration] = None,
        tombstoneThreshold: Option[Double]            = None,
        uncheckedTombstoneCompaction: Option[Boolean] = None,
        logAll: Option[Boolean]                       = None,
        ssTableSizeInMb: Option[Int]                  = None,
        fanoutSize: Option[Int]                       = None,
        singleSSTableUplevel: Option[Boolean]         = None
    ) extends CompactionStrategy {
      override def cqlParams: Map[String, String] = Seq(
        Some("class" -> "LeveledCompactionStrategy"),
        enabled.map(v => "enabled" -> v.toString),
        tombstoneCompactionInterval.map(v =>
          "tombstone_compaction_interval" -> v.toSeconds.toString
        ),
        tombstoneThreshold.map(v => "tombstone_threshold" -> v.toString),
        uncheckedTombstoneCompaction.map(v => "unchecked_tombstone_compaction" -> v.toString),
        logAll.map(v => "log_all" -> v.toString),
        ssTableSizeInMb.map(v => "sstable_size_in_mb" -> v.toString),
        fanoutSize.map(v => "fanout_size" -> v.toString),
        singleSSTableUplevel.map(v => "single_sstable_uplevel" -> v.toString)
      ).flatten.toMap
    }

    case class TimeWindowCompactionStrategy(
        enabled: Option[Boolean]                              = None,
        tombstoneCompactionInterval: Option[Duration]         = None,
        tombstoneThreshold: Option[Double]                    = None,
        uncheckedTombstoneCompaction: Option[Boolean]         = None,
        logAll: Option[Boolean]                               = None,
        compactionWindowUnit: Option[TimeUnit]                = None,
        compactionWindowSize: Option[Int]                     = None,
        timestampResolution: Option[ChronoUnit]               = None,
        expiredSSTableCheckFrequencySeconds: Option[Duration] = None,
        unsafeAggressiveSSTableExpiration: Option[Boolean]    = None
    ) extends CompactionStrategy {
      override def cqlParams: Map[String, String] = Seq(
        Some("class" -> "TimeWindowCompactionStrategy"),
        enabled.map(v => "enabled" -> v.toString),
        tombstoneCompactionInterval.map(v =>
          "tombstone_compaction_interval" -> v.toSeconds.toString
        ),
        tombstoneThreshold.map(v => "tombstone_threshold" -> v.toString),
        uncheckedTombstoneCompaction.map(v => "unchecked_tombstone_compaction" -> v.toString),
        logAll.map(v => "log_all" -> v.toString),
        compactionWindowUnit.map(v => "compaction_window_unit" -> v.toString),
        compactionWindowSize.map(v => "compaction_window_size" -> v.toString),
        timestampResolution.map(v => "timestamp_resolution" -> v.toString),
        expiredSSTableCheckFrequencySeconds.map(v =>
          "expired_sstable_check_frequency_seconds" -> v.toString
        ),
        unsafeAggressiveSSTableExpiration.map(v =>
          "unsafe_aggressive_sstable_expiration" -> v.toString
        )
      ).flatten.toMap
    }
  }

  sealed abstract class ReadRepair(val value: String)
  object ReadRepair {
    object Blocking extends ReadRepair("BLOCKING")
    object None extends ReadRepair("NONE")
  }

  /** A single-quoted CQL string, escaping any embedded quote by doubling it. */
  private def quote(value: String): String = "'" + value.replace("'", "''") + "'"

  /** A CQL string-map literal (`{'k': 'v', ...}`) with keys sorted for stable
    * output; both keys and values are string-quoted, as the server expects for
    * `caching` / `compaction` / `compression`.
    */
  private def renderMap(m: Map[String, String]): String =
    m.toSeq
      .sortBy(_._1)
      .map { case (k, v) => s"${quote(k)}: ${quote(v)}" }
      .mkString("{", ", ", "}")
}
