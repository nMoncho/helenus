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

package net.nmoncho.helenus
package bench

import java.util.concurrent.TimeUnit

import scala.util.Random

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.data.TupleValue
import com.datastax.oss.driver.api.core.detach.AttachmentPoint
import com.datastax.oss.driver.internal.core.`type`.DefaultTupleType
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class TupleCodecBenchmark {

  import org.mockito.Mockito._

  import scala.jdk.CollectionConverters._

  private val attachmentPoint = mock(classOf[AttachmentPoint])
  private val codecRegistry   = mock(classOf[CodecRegistry])
  private val tupleType =
    new DefaultTupleType(
      List(DataTypes.INT, DataTypes.INT, DataTypes.INT, DataTypes.INT, DataTypes.INT).asJava,
      attachmentPoint
    )
  when(attachmentPoint.getCodecRegistry).thenReturn(codecRegistry)
  when(attachmentPoint.getProtocolVersion).thenReturn(ProtocolVersion.DEFAULT)

  // Called by the getters/setters
  when(codecRegistry.codecFor(DataTypes.INT, classOf[java.lang.Integer]))
    .thenReturn(TypeCodecs.INT)

  // Called by format/parse
  when(codecRegistry.codecFor[java.lang.Integer](DataTypes.INT)).thenReturn(TypeCodecs.INT)

  type Input = (Int, Int, Int, Int, Int)

  private val rnd                  = new Random(0)
  private val dseCodec             = TypeCodecs.tupleOf(tupleType)
  private val codec                = Codec.tupleOf[Input]
  private var input: Input         = _
  private var dseInput: TupleValue = _

  private var _1: Int = _
  private var _2: Int = _
  private var _3: Int = _
  private var _4: Int = _
  private var _5: Int = _

  @Setup
  def prepare(): Unit = {
    _1    = rnd.nextInt()
    _2    = rnd.nextInt()
    _3    = rnd.nextInt()
    _4    = rnd.nextInt()
    _5    = rnd.nextInt()
    input = (_1, _2, _3, _4, _5)
  }

  @Benchmark
  def baseline(blackHole: Blackhole): Unit = {
    dseInput = tupleType
      .newValue()
      .setInt(0, _1)
      .setInt(1, _2)
      .setInt(2, _3)
      .setInt(3, _4)
      .setInt(4, _5)

    blackHole.consume(
      dseCodec.decode(dseCodec.encode(dseInput, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
  }

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(
      codec.decode(codec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
}
