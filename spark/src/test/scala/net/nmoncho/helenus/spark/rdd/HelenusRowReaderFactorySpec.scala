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
import net.nmoncho.helenus.api.RowMapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit-level coverage of the read bridge, with no Cassandra or Spark: it exercises
  * the factory/reader contract and the serialization property directly.
  *
  * The end-to-end acceptance — `sc.cassandraTable[Hotel]("ks", "hotels")` returning an
  * `RDD[Hotel]` (UDT column included), one derivation per RDD, and a real cross-executor
  * run with no `NotSerializableException` — lands with the embedded-Cassandra + local
  * Spark harness, and the implicit entry point.
  */
final class HelenusRowReaderFactorySpec extends AnyWordSpec with Matchers {

  // A fake mapper (not derived) so the test needs no codecs; its value is a top-level
  // class with no captured outer reference, so serializing a factory stays clean.
  private implicit val sampleMapper: RowMapper[ReaderSample] = new ReaderSampleMapper

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
      new HelenusRowReaderFactory[ReaderSample].targetClass shouldBe classOf[ReaderSample]
    }

    "let the connector own column selection (neededColumns = None)" in {
      val reader = new HelenusRowReaderFactory[ReaderSample].rowReader(null, IndexedSeq.empty)
      reader.neededColumns shouldBe None
    }

    "materialize a single reader, reused across partitions" in {
      val factory = new HelenusRowReaderFactory[ReaderSample]
      val first   = factory.rowReader(null, IndexedSeq.empty)
      val second  = factory.rowReader(null, IndexedSeq.empty)

      first should be theSameInstanceAs second
    }

    "delegate read to the Helenus RowMapper" in {
      ReaderSampleMapper.calls.set(0)
      val reader = new HelenusRowReaderFactory[ReaderSample].rowReader(null, IndexedSeq.empty)

      // The mapper ignores the row, so a null Row is safe here; delegation is what matters.
      reader.read(null, null) shouldBe ReaderSample(42)
      ReaderSampleMapper.calls.get() shouldBe 1
    }

    "serialize the factory and its reader (shippable to executors)" in {
      val factory        = new HelenusRowReaderFactory[ReaderSample]
      val restoredReader = roundTrip(factory.rowReader(null, IndexedSeq.empty))

      restoredReader.read(null, null) shouldBe ReaderSample(42)
      roundTrip(factory).targetClass shouldBe classOf[ReaderSample]
    }
  }
}

/** Top-level fixtures (no enclosing instance to capture) so serialization stays clean. */
final case class ReaderSample(id: Int)

object ReaderSampleMapper {
  val calls: AtomicInteger = new AtomicInteger(0)
}

/** A mapper that ignores the row so tests need no real driver `Row`; it counts calls to
  * prove the reader delegates, and is a named top-level class so it ships without a
  * captured outer reference.
  */
final class ReaderSampleMapper extends RowMapper[ReaderSample] {
  override def apply(row: Row): ReaderSample = {
    ReaderSampleMapper.calls.incrementAndGet()
    ReaderSample(42)
  }
}
