/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.bench

import java.util.concurrent.TimeUnit

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.internal.core.`type`.codec.{ FloatCodec => DseFloatCodec }
import net.nmoncho.helenus.internal.codec.FloatCodec
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class FloatCodecBenchmark {

  private var input: Float = 0
  private val dseCodec     = new DseFloatCodec()

  @Setup
  def prepare(): Unit = input = Math.random().toFloat

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    blackHole.consume(
      dseCodec.decode(dseCodec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(
      FloatCodec.decode(FloatCodec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
}
