/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus.bench.collection

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.{ TypeCodecs => DseTypeCodecs }
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import java.util
import java.util.concurrent.TimeUnit
import scala.util.Random

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class MapCodecBenchmark {

  // format: off
  @Param(Array(
    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
    "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
    "40"))
  private var tokens = 0
  // format: on

  private val rnd                                                          = new Random(0)
  private var dseInput: util.HashMap[java.lang.Integer, java.lang.Integer] = _
  private var input: Map[Int, Int]                                         = _
  private val dseCodec = DseTypeCodecs.mapOf(DseTypeCodecs.INT, DseTypeCodecs.INT)
  private val codec    = TypeCodecs.mapOf(TypeCodecs.intCodec, TypeCodecs.intCodec)

  @Setup
  def prepare(): Unit = {
    dseInput = new util.HashMap[java.lang.Integer, java.lang.Integer]()
    input    = Map()
    for (_ <- 0 until (tokens + 1)) {
      val key   = rnd.nextInt()
      val value = rnd.nextInt()
      dseInput.put(key, value)
      input = input + (key -> value)
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
