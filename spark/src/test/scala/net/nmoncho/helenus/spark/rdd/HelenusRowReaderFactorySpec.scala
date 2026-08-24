/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.rdd

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.atomic.AtomicInteger

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.spark.connector.rdd.reader.RowReader
import net.nmoncho.helenus.api.RowMapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit-level coverage of the B1 read bridge, with no Cassandra or Spark: it exercises
  * the factory/reader contract, the "derive once per reader, not per row" property, and
  * the shipping property (a reader serializes even though its materialized mapper would
  * not) directly.
  *
  * The end-to-end acceptance — `sc.cassandraTable[Hotel]("ks", "hotels")` returning an
  * `RDD[Hotel]` (UDT column included), one derivation per partition, and a real
  * cross-executor run — lives in [[HelenusCassandraTableSpec]] against embedded Cassandra
  * and a local Spark context.
  */
final class HelenusRowReaderFactorySpec extends AnyWordSpec with Matchers {

  private def factory: HelenusRowReaderFactory[ReaderSample] =
    HelenusRowReaderFactory(new ReaderSampleMapper)

  private def newReader(): RowReader[ReaderSample] =
    factory.rowReader(null, IndexedSeq.empty)

  private def roundTrip[A](value: A): A = {
    val bytes = new ByteArrayOutputStream()
    val out   = new ObjectOutputStream(bytes)
    out.writeObject(value)
    out.close()

    val in       = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray))
    val restored = in.readObject().asInstanceOf[A]
    in.close()
    restored
  }

  "HelenusRowReaderFactory" should {
    "expose the target class from the ClassTag" in {
      factory.targetClass shouldBe classOf[ReaderSample]
    }

    "let the connector own column selection without deriving the mapper" in {
      ReaderSampleMapper.constructions.set(0)

      newReader().neededColumns shouldBe None
      // Building the reader and asking for columns must not force the (driver-side) mapper.
      ReaderSampleMapper.constructions.get() shouldBe 0
    }

    "derive the mapper once per reader, reusing it for every row" in {
      ReaderSampleMapper.constructions.set(0)
      ReaderSampleMapper.applies.set(0)
      val reader = newReader()

      // The mapper ignores the row, so a null Row is safe here; delegation is what matters.
      reader.read(null, null) shouldBe ReaderSample(42)
      reader.read(null, null) shouldBe ReaderSample(42)

      ReaderSampleMapper.constructions.get() shouldBe 1 // derived once
      ReaderSampleMapper.applies.get() shouldBe 2 // reused per row
    }

    "serialize a reader without shipping the mapper, then re-derive on the far side" in {
      // ReaderSampleMapper holds a non-serializable field, so a *materialized* mapper
      // cannot cross the wire — exactly the connector's driver-to-executor situation. The
      // reader still serializes because the mapper is a transient lazy val behind a
      // serializable provider, and it re-derives after deserialization.
      ReaderSampleMapper.constructions.set(0)
      val reader = newReader()
      reader.read(null, null) // force derivation on this side

      val restored = roundTrip(reader)
      restored.read(null, null) shouldBe ReaderSample(42)

      ReaderSampleMapper.constructions.get() shouldBe 2 // once here, once after deserialize
    }
  }
}

/** Top-level fixtures (no enclosing instance to capture) so the provider lambda and any
  * serialized reader stay clean.
  */
final case class ReaderSample(id: Int)

object ReaderSampleMapper {
  val constructions: AtomicInteger = new AtomicInteger(0)
  val applies: AtomicInteger       = new AtomicInteger(0)
}

/** A mapper that ignores the row so tests need no real driver `Row`. It holds a
  * non-serializable field, so a materialized instance cannot be serialized — proving the
  * reader ships the provider rather than the mapper. It counts constructions (to prove the
  * reader derives once) and applies (to prove per-row reuse), and is a named top-level
  * class so the provider lambda that builds it carries no outer reference.
  */
final class ReaderSampleMapper extends RowMapper[ReaderSample] {
  ReaderSampleMapper.constructions.incrementAndGet()

  // A plain Object does not implement Serializable, so serializing this instance fails.
  private val nonSerializable: AnyRef = new Object

  override def apply(row: Row): ReaderSample = {
    ReaderSampleMapper.applies.incrementAndGet()
    nonSerializable.hashCode() // keep the field reachable so it is not optimized away
    ReaderSample(42)
  }
}
