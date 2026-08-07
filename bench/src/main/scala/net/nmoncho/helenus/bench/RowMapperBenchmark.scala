/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package bench

import java.util.concurrent.TimeUnit

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/** Maps a single [[Row]] into a wide case class.
  *
  * The [[Row]] is a Mockito stub returning fixed values by column name, so the benchmark isolates
  * the mapping cost (per-row HList reconstruction and, for `benchDerive`, the derivation itself)
  * from any driver I/O. The stub's per-call cost is identical across all three variants (they read
  * the same columns), so it is a constant offset: compare the variants against each other rather
  * than reading the absolute scores.
  *
  *   - `baseline`   : a hand-written [[RowMapper]] reading each column directly.
  *   - `bench`      : a once-derived, reused [[RowMapper]] (the recommended pattern, see
  *                    `RowMapper.of` / `RowMapper.cached`), measuring steady-state per-row cost.
  *   - `benchDerive`: re-derives the mapper on every call, showing the overhead avoided by
  *                    binding the mapper once.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class RowMapperBenchmark {

  import RowMapperBenchmark._

  import org.mockito.ArgumentMatchers._
  import org.mockito.Mockito._
  import org.mockito.invocation.InvocationOnMock

  private val values: Map[String, Any] = Map(
    "id" -> "a1b2c3",
    "name" -> "helenus",
    "count" -> 42,
    "amount" -> 9001L,
    "ratio" -> 0.75d,
    "active" -> true,
    "score" -> 1.5f,
    "label" -> "gold"
  )

  private val row: Row = {
    val r = mock(classOf[Row])
    when(r.get(anyString(), any[TypeCodec[Any]]())).thenAnswer { (inv: InvocationOnMock) =>
      values(inv.getArgument[String](0)).asInstanceOf[AnyRef]
    }
    r
  }

  // Derived once; this is the pattern the docs recommend for hot loops.
  private val reusedMapper: RowMapper[WideRow] = RowMapper.of[WideRow]

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    blackHole.consume(baselineMapper(row))

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(reusedMapper(row))

  @Benchmark
  def benchDerive(blackHole: Blackhole): Unit =
    blackHole.consume(RowMapper.of[WideRow].apply(row))
}

object RowMapperBenchmark {

  final case class WideRow(
      id: String,
      name: String,
      count: Int,
      amount: Long,
      ratio: Double,
      active: Boolean,
      score: Float,
      label: String
  )

  /** Hand-written mapper equivalent to the derived one, used as the baseline. */
  private val baselineMapper: RowMapper[WideRow] = (row: Row) =>
    WideRow(
      row.getCol[String]("id"),
      row.getCol[String]("name"),
      row.getCol[Int]("count"),
      row.getCol[Long]("amount"),
      row.getCol[Double]("ratio"),
      row.getCol[Boolean]("active"),
      row.getCol[Float]("score"),
      row.getCol[String]("label")
    )
}
