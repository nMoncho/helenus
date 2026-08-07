/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package bench
package udt

import java.util.concurrent.TimeUnit

import scala.util.Random

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.data.UdtValue
import com.datastax.oss.driver.api.core.detach.AttachmentPoint
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import com.datastax.oss.driver.internal.core.`type`.codec.{ UdtCodec => DseUdtCodec }
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/** Encode/decode round-trip for a case class mapped to a UDT.
  *
  * `baseline` uses the DataStax driver's own [[UdtValue]]/[[DseUdtCodec]] path; `bench` uses the
  * helenus derived codec (the identical, same-field-order path optimized in E1). Both round-trip a
  * value and read every field back, so the two measurements are comparable.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class UdtCodecBenchmark {

  import org.mockito.Mockito._

  import scala.jdk.CollectionConverters._

  private val attachmentPoint = mock(classOf[AttachmentPoint])
  private val codecRegistry   = mock(classOf[CodecRegistry])

  when(attachmentPoint.getCodecRegistry).thenReturn(codecRegistry)
  when(attachmentPoint.getProtocolVersion).thenReturn(ProtocolVersion.DEFAULT)

  // Field codecs, resolved by the UdtValue getters/setters (by type + class) and by
  // format/parse (by type only), mirroring TupleCodecBenchmark.
  when(codecRegistry.codecFor(DataTypes.TEXT, classOf[String])).thenReturn(TypeCodecs.TEXT)
  when(codecRegistry.codecFor[String](DataTypes.TEXT)).thenReturn(TypeCodecs.TEXT)
  when(codecRegistry.codecFor(DataTypes.INT, classOf[java.lang.Integer])).thenReturn(TypeCodecs.INT)
  when(codecRegistry.codecFor[java.lang.Integer](DataTypes.INT)).thenReturn(TypeCodecs.INT)
  when(codecRegistry.codecFor(DataTypes.BOOLEAN, classOf[java.lang.Boolean]))
    .thenReturn(TypeCodecs.BOOLEAN)
  when(codecRegistry.codecFor[java.lang.Boolean](DataTypes.BOOLEAN)).thenReturn(TypeCodecs.BOOLEAN)
  when(codecRegistry.codecFor(DataTypes.DOUBLE, classOf[java.lang.Double]))
    .thenReturn(TypeCodecs.DOUBLE)
  when(codecRegistry.codecFor[java.lang.Double](DataTypes.DOUBLE)).thenReturn(TypeCodecs.DOUBLE)

  private val udtType = new DefaultUserDefinedType(
    CqlIdentifier.fromInternal("bench"),
    CqlIdentifier.fromInternal("sundae"),
    false,
    List(
      CqlIdentifier.fromInternal("name"),
      CqlIdentifier.fromInternal("numCherries"),
      CqlIdentifier.fromInternal("cone"),
      CqlIdentifier.fromInternal("scoops"),
      CqlIdentifier.fromInternal("weightGrams")
    ).asJava,
    List(DataTypes.TEXT, DataTypes.INT, DataTypes.BOOLEAN, DataTypes.INT, DataTypes.DOUBLE).asJava,
    attachmentPoint
  )

  private val dseCodec = new DseUdtCodec(udtType)
  private val codec    = Codec.of[UdtCodecBenchmark.Sundae]()

  private val rnd                             = new Random(0)
  private var input: UdtCodecBenchmark.Sundae = _

  @Setup
  def prepare(): Unit =
    input = UdtCodecBenchmark.Sundae(
      name        = rnd.nextString(8),
      numCherries = rnd.nextInt(),
      cone        = rnd.nextBoolean(),
      scoops      = rnd.nextInt(),
      weightGrams = rnd.nextDouble()
    )

  @Benchmark
  def baseline(blackHole: Blackhole): Unit = {
    val value: UdtValue = udtType
      .newValue()
      .setString(0, input.name)
      .setInt(1, input.numCherries)
      .setBoolean(2, input.cone)
      .setInt(3, input.scoops)
      .setDouble(4, input.weightGrams)

    val decoded =
      dseCodec.decode(dseCodec.encode(value, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

    // Read every field back so the baseline materializes as much as `bench` does.
    blackHole.consume(
      (
        decoded.getString(0),
        decoded.getInt(1),
        decoded.getBoolean(2),
        decoded.getInt(3),
        decoded.getDouble(4)
      )
    )
  }

  @Benchmark
  def bench(blackHole: Blackhole): Unit =
    blackHole.consume(
      codec.decode(codec.encode(input, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
    )
}

object UdtCodecBenchmark {
  final case class Sundae(
      name: String,
      numCherries: Int,
      cone: Boolean,
      scoops: Int,
      weightGrams: Double
  )
}
