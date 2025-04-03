/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.bench.collection

import java.util
import java.util.concurrent.TimeUnit

import scala.util.Random

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.{ TypeCodecs => DseTypeCodecs }
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class SeqCodecBenchmark {

  // format: off
  @Param(Array(
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
    "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
    "40"))
  private var tokens = 0
  // format: on

  private val rnd                                          = new Random(0)
  private var dseInput: util.LinkedList[java.lang.Integer] = _
  private var input: Seq[Int]                              = _
  private val dseCodec                                     = DseTypeCodecs.listOf(DseTypeCodecs.INT)
  private val codec                                        = TypeCodecs.seqOf(TypeCodecs.intCodec)

  @Setup
  def prepare(): Unit = {
    dseInput = new util.LinkedList[java.lang.Integer]()
    input    = Seq()
    for (_ <- 0 until (tokens + 1)) {
      val item = rnd.nextInt()
      dseInput.addFirst(item)
      input = item +: input
    }
  }

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    blackHole.consume(
      dseCodec.decode(dseCodec.encode(dseInput, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(
      codec.decode(codec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
}
