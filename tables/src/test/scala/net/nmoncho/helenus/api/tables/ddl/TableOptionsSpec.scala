/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package ddl

import java.time.Duration
import java.util.concurrent.TimeUnit

import net.nmoncho.helenus.api.tables.ddl.TableOptions._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Verifies the typed `WITH` options render to the CQL fragments the server expects. Pure
  * string comparison, no Cassandra: each option exposes a `cqlParams` / `value` that
  * [[TableOptions.render]] turns into a `name = value` fragment (map options as sorted,
  * quoted string maps).
  */
class TableOptionsSpec extends AnyWordSpec with Matchers {

  /** The single rendered fragment of an options object that sets exactly one option. */
  private def fragment(options: TableOptions): String = {
    options.render.size shouldBe 1
    options.render.head
  }

  "Caching" should {
    "render keys and rows_per_partition" in {
      Caching(Caching.Keys.All, Caching.Rows.None).cqlParams shouldBe
      Map("keys" -> "ALL", "rows_per_partition" -> "NONE")

      Caching(Caching.Keys.None, Caching.Rows.All).cqlParams shouldBe
      Map("keys" -> "NONE", "rows_per_partition" -> "ALL")
    }

    "render a row amount as a plain number" in {
      Caching(Caching.Keys.All, Caching.Rows.Amount(100)).cqlParams("rows_per_partition") shouldBe
      "100"
    }

    "render as a sorted, quoted CQL string map" in {
      fragment(TableOptions(caching = Some(Caching(Caching.Keys.All, Caching.Rows.None)))) shouldBe
      "caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}"
    }
  }

  "SpeculativeRetry" should {
    "render each variant's value" in {
      SpeculativeRetry.None.value shouldBe "NONE"
      SpeculativeRetry.Always.value shouldBe "ALWAYS"
      SpeculativeRetry.Percentile(99).value shouldBe "99PERCENTILE"
      SpeculativeRetry.Ms(50).value shouldBe "50MS"
      SpeculativeRetry.Min(99, 50).value shouldBe "MIN(99PERCENTILE,50MS)"
      SpeculativeRetry.Max(90, 25).value shouldBe "MAX(90PERCENTILE,25MS)"
    }

    "render as a quoted speculative_retry / additional_write_policy option" in {
      fragment(TableOptions(speculativeRetry = Some(SpeculativeRetry.Percentile(99)))) shouldBe
      "speculative_retry = '99PERCENTILE'"

      fragment(TableOptions(additionalWritePolicy = Some(SpeculativeRetry.None))) shouldBe
      "additional_write_policy = 'NONE'"
    }
  }

  "ReadRepair" should {
    "render its value quoted" in {
      ReadRepair.Blocking.value shouldBe "BLOCKING"
      ReadRepair.None.value shouldBe "NONE"

      fragment(TableOptions(readRepair = Some(ReadRepair.Blocking))) shouldBe
      "read_repair = 'BLOCKING'"
    }
  }

  "Compression" should {
    "disable compression" in {
      Compression.Disabled.cqlParams shouldBe Map("enabled" -> "false")
      fragment(TableOptions(compression = Some(Compression.Disabled))) shouldBe
      "compression = {'enabled': 'false'}"
    }

    "render the LZ4 compressor with its class, chunk size, type and level" in {
      Compression
        .LZ4Compressor(64, Compression.LZ4Compressor.CompressorType.Fast, 9)
        .cqlParams shouldBe Map(
        "class" -> "LZ4Compressor",
        "chunk_length_in_kb" -> "64",
        "lz4_compressor_type" -> "fast",
        "lz4_high_compressor_level" -> "9"
      )

      fragment(
        TableOptions(compression =
          Some(Compression.LZ4Compressor(64, Compression.LZ4Compressor.CompressorType.High, 9))
        )
      ) shouldBe
      "compression = {'chunk_length_in_kb': '64', 'class': 'LZ4Compressor', " +
      "'lz4_compressor_type': 'high', 'lz4_high_compressor_level': '9'}"
    }

    "render the Zstd, Snappy and Deflate compressors" in {
      Compression.ZstdCompressor(64, 3).cqlParams shouldBe
      Map("class" -> "ZstdCompressor", "chunk_length_in_kb" -> "64", "compression_level" -> "3")

      Compression.SnappyCompressor(64).cqlParams shouldBe
      Map("class" -> "SnappyCompressor", "chunk_length_in_kb" -> "64")

      Compression.DeflateCompressor(64).cqlParams shouldBe
      Map("class" -> "DeflateCompressor", "chunk_length_in_kb" -> "64")
    }

    "reject invalid compressor arguments" in {
      an[IllegalArgumentException] should be thrownBy
      Compression.LZ4Compressor(0, Compression.LZ4Compressor.CompressorType.Fast, 9)
      an[IllegalArgumentException] should be thrownBy
      Compression.LZ4Compressor(64, Compression.LZ4Compressor.CompressorType.Fast, 0)
      an[IllegalArgumentException] should be thrownBy
      Compression.LZ4Compressor(64, Compression.LZ4Compressor.CompressorType.Fast, 18)
      an[IllegalArgumentException] should be thrownBy Compression.ZstdCompressor(64, 23)
      an[IllegalArgumentException] should be thrownBy Compression.ZstdCompressor(64, -131073)
      an[IllegalArgumentException] should be thrownBy Compression.SnappyCompressor(0)
      an[IllegalArgumentException] should be thrownBy Compression.DeflateCompressor(0)
    }
  }

  "CompactionStrategy" should {
    import CompactionStrategy._

    "render just the class when no field is set" in {
      LeveledCompactionStrategy().cqlParams shouldBe Map("class" -> "LeveledCompactionStrategy")
      fragment(TableOptions(compaction = Some(LeveledCompactionStrategy()))) shouldBe
      "compaction = {'class': 'LeveledCompactionStrategy'}"
    }

    "render Leveled fields that are set" in {
      fragment(
        TableOptions(compaction =
          Some(LeveledCompactionStrategy(ssTableSizeInMb = Some(160), fanoutSize = Some(10)))
        )
      ) shouldBe
      "compaction = {'class': 'LeveledCompactionStrategy', " +
      "'fanout_size': '10', 'sstable_size_in_mb': '160'}"
    }

    "render SizeTiered thresholds, a tombstone ratio and a duration in seconds" in {
      SizeTieredCompactionStrategy(
        minThreshold = Some(4),
        maxThreshold = Some(32),
        // `tombstone_threshold` is a ratio (a Double), not a duration.
        tombstoneThreshold          = Some(0.2),
        tombstoneCompactionInterval = Some(Duration.ofDays(1))
      ).cqlParams shouldBe Map(
        "class" -> "SizeTieredCompactionStrategy",
        "min_threshold" -> "4",
        "max_threshold" -> "32",
        "tombstone_threshold" -> "0.2",
        "tombstone_compaction_interval" -> "86400"
      )
    }

    "render Unified scaling parameters, byte sizes and the overlap method" in {
      fragment(
        TableOptions(compaction =
          Some(
            UnifiedCompactionStrategy(
              scalingParameters      = Seq(2, 4),
              targetSSTableSize      = Some(Bytes(1, GiB)),
              overlapInclusionMethod = Some(OverlapInclusionMethod.Transitive)
            )
          )
        )
      ) shouldBe
      "compaction = {'class': 'UnifiedCompactionStrategy', " +
      "'overlap_inclusion_method': 'TRANSITIVE', " +
      "'scaling_parameters': '[2,4]', 'target_sstable_size': '1GiB'}"
    }

    "render TimeWindow window unit, size and the expired-check frequency in seconds" in {
      TimeWindowCompactionStrategy(
        compactionWindowUnit = Some(TimeUnit.DAYS),
        compactionWindowSize = Some(1),
        // Rendered in plain seconds, matching UnifiedCompactionStrategy (not `Duration.toString`).
        expiredSSTableCheckFrequencySeconds = Some(Duration.ofMinutes(10))
      ).cqlParams shouldBe Map(
        "class" -> "TimeWindowCompactionStrategy",
        "compaction_window_unit" -> "DAYS",
        "compaction_window_size" -> "1",
        "expired_sstable_check_frequency_seconds" -> "600"
      )
    }

    "render byte sizes with their unit" in {
      Bytes(100, MiB).toCQL shouldBe "100MiB"
      Bytes(512, KiB).toCQL shouldBe "512KiB"
      Bytes(2, GB).toCQL shouldBe "2GB"
    }
  }

  "TableOptions.render" should {
    "be empty when nothing is set" in {
      TableOptions.Empty.isEmpty shouldBe true
      TableOptions.Empty.render shouldBe empty
    }

    "emit set options in canonical order, independent of case-class argument grouping" in {
      TableOptions(
        comment        = Some("audit"),
        gcGraceSeconds = Some(Duration.ofSeconds(100)),
        cdc            = Some(true),
        compaction     = Some(CompactionStrategy.LeveledCompactionStrategy())
      ).render shouldBe Seq(
        "comment = 'audit'",
        "gc_grace_seconds = 100",
        "cdc = true",
        "compaction = {'class': 'LeveledCompactionStrategy'}"
      )
    }
  }
}
