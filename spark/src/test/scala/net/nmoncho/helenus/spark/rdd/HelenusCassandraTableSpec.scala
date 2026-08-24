/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.rdd

import java.util.concurrent.atomic.AtomicInteger

import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.reader.RowReaderFactory
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.spark._
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** End-to-end B1 acceptance: `sc.cassandraTable[Hotel]` returns an `RDD[Hotel]` mapped by
  * Helenus codecs — UDT column included — against embedded Cassandra and a local Spark
  * context. The connector owns the token-aware scan; Helenus owns only the `Row => Hotel`
  * conversion via [[HelenusRowReaderFactory]].
  *
  * Because the whole job runs on `local[2]`, Spark serializes the scan RDD (factory,
  * provider, `ClassTag`) into the task binary and deserializes it before computing each
  * partition — so a green run is the proof that the bridge ships to a real executor
  * without `NotSerializableException`, and that the mapper is (re)derived on the executor
  * rather than being shipped materialized. It is also the live proof of the D2 classpath
  * decision: Helenus statements and codecs run against the connector's shaded driver.
  */
final class HelenusCassandraTableSpec extends AnyWordSpec with Matchers with CassandraSpec {

  private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    super.beforeAll() // creates the keyspace and `USE`s it
    executeFile("hotels.cql")
    HotelsTestData.insertTestData()(session)

    val conf = new SparkConf()
      .setMaster("local[2]")
      .setAppName("helenus-spark-b1")
      .set("spark.ui.enabled", "false")
      .set("spark.driver.host", "localhost")
      .set("spark.driver.bindAddress", "localhost")
      .set("spark.cassandra.connection.host", "localhost")
      .set("spark.cassandra.connection.port", "9142")
      .set("spark.cassandra.connection.localDC", "datacenter1")

    spark = SparkSession.builder().config(conf).getOrCreate()
  }

  override def afterAll(): Unit = {
    if (spark != null) spark.stop()
    super.afterAll()
  }

  // Data is inserted once in `beforeAll` and both read tests share it, so skip
  // CassandraSpec's per-test truncation.
  override def afterEach(): Unit = ()

  "sc.cassandraTable[Hotel]" should {
    "return an RDD[Hotel] mapped by Helenus codecs, including the UDT column" in {
      CountingHotelMapper.reset()

      // Pass the derivation by name so it is rebuilt on the executor; the connector picks
      // this factory up as the implicit RowReaderFactory[Hotel] at the call site.
      implicit val rrf: RowReaderFactory[Hotel] =
        HelenusRowReaderFactory(new CountingHotelMapper)

      val rdd           = spark.sparkContext.cassandraTable[Hotel](keyspace, "hotels")
      val numPartitions = rdd.getNumPartitions
      val hotels        = rdd.collect().toList

      // Every row mapped correctly, including the frozen `address` UDT and the `pois` set.
      hotels should contain theSameElementsAs HotelsTestData.Hotels.all
      hotels.find(_.id == "h1").map(_.address) shouldBe Some(HotelsTestData.Hotels.h1.address)
      hotels.find(_.id == "h1").map(_.pois) shouldBe Some(HotelsTestData.Hotels.h1.pois)

      // One derivation per partition, reused for every row: the mapper is constructed at
      // most once per partition (never per row), yet applied exactly once per row.
      CountingHotelMapper.applies.get() shouldBe HotelsTestData.Hotels.all.size
      CountingHotelMapper.providerCalls.get() should be >= 1
      CountingHotelMapper.providerCalls.get() should be <= numPartitions
    }

    "resolve the bridge via the helenusRowReaderFactory entry point, outranking the connector default (B2)" in {
      CountingHotelMapper.reset()

      // `helenusRowReaderFactory` is the `net.nmoncho.helenus.spark` package-object entry
      // point. Bound as a local implicit it outranks the connector's own default
      // RowReaderFactory (`import com.datastax.spark.connector._`, imported above), so the
      // call resolves unambiguously — and the non-zero apply count proves the Helenus
      // mapper, not the connector's, actually mapped the rows.
      implicit val rrf: RowReaderFactory[Hotel] = helenusRowReaderFactory(new CountingHotelMapper)

      val hotels = spark.sparkContext.cassandraTable[Hotel](keyspace, "hotels").collect().toList

      hotels should contain theSameElementsAs HotelsTestData.Hotels.all
      CountingHotelMapper.applies.get() shouldBe HotelsTestData.Hotels.all.size
    }
  }
}

/** Counters shared in-JVM: on `local[2]` the executor threads live in the driver JVM, so
  * the driver can read these after the job completes.
  */
object CountingHotelMapper {
  val providerCalls: AtomicInteger = new AtomicInteger(0)
  val applies: AtomicInteger       = new AtomicInteger(0)

  def reset(): Unit = {
    providerCalls.set(0)
    applies.set(0)
  }
}

/** Wraps the real, derived `Hotel.rowMapper`. Constructed on the executor by the factory's
  * provider (once per partition), it counts constructions and per-row applies, and is a
  * named top-level class so the provider lambda that builds it carries no outer reference.
  * The wrapped mapper is only ever used on the executor, so its non-serializable driver
  * codecs never cross the wire.
  */
final class CountingHotelMapper extends RowMapper[Hotel] {
  CountingHotelMapper.providerCalls.incrementAndGet()

  private val underlying: RowMapper[Hotel] = Hotel.rowMapper

  override def apply(row: Row): Hotel = {
    CountingHotelMapper.applies.incrementAndGet()
    underlying(row)
  }
}
