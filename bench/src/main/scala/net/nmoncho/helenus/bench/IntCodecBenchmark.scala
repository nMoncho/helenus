/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.bench

import java.util.concurrent.TimeUnit

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.internal.core.`type`.codec.{ IntCodec => DseIntCodec }
import net.nmoncho.helenus.internal.codec.IntCodec
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class IntCodecBenchmark {

  private var input: Int = 0
  private val dseCodec   = new DseIntCodec()

  @Setup
  def prepare(): Unit = input = Math.random().toInt

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    blackHole.consume(
      dseCodec.decode(dseCodec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(
      IntCodec.decode(IntCodec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
}
