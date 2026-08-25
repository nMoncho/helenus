/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark.sink

import java.util.concurrent.atomic.AtomicInteger

import com.datastax.oss.driver.api.core.cql.SimpleStatement
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.cql.Mapping
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.spark._
import net.nmoncho.helenus.utils.CassandraSpec
import net.nmoncho.helenus.utils.HotelsTestData
import org.apache.spark.SparkContext
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Encoders
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** End-to-end acceptance for the CQL-first `foreachPartitionCql` sink, against
  * embedded Cassandra and a local Spark context. It covers exactly what `saveToCassandra`
  * cannot express: an LWT `IF NOT EXISTS` insert whose applied/not-applied result is
  * observable in the table, a conditional `IF EXISTS` delete, and arbitrary CQL (a
  * collection append). Because the job runs on `local[2]`, a green run also proves the
  * statement-builder closure and its captured codecs serialize to a real executor.
  */
final class HelenusForeachPartitionCqlSpec extends AnyWordSpec with Matchers with CassandraSpec {

  // The connector's session has no default keyspace, so the sink's CQL qualifies the table.
  // `toCQL` needs a plain string literal (not even constant concatenation), so the keyspace
  // name is inlined in each statement below and the test keyspace is pinned to match it
  // rather than being random.
  override protected lazy val keyspace: String = "helenus_spark_c3"

  private def sc: SparkContext = LocalSpark.session.sparkContext

  override def beforeAll(): Unit = {
    // The keyspace is fixed (not random), and the embedded node is shared across the
    // 2.12 and 2.13 runs, so drop any schema a previous run left before recreating it.
    session.execute(s"DROP KEYSPACE IF EXISTS $keyspace")
    super.beforeAll() // (re)creates the keyspace and `USE`s it
    executeFile("hotels.cql")
  }

  "foreachPartitionCql" should {
    "apply an LWT IF NOT EXISTS insert, with applied/not-applied observable in the table" in {
      // h1 already exists with its original name; the sink then tries to (re)insert a
      // changed h1 and a brand-new h2, both guarded by IF NOT EXISTS.
      insertHotel(HotelsTestData.Hotels.h1)
      val changedH1 = HotelsTestData.Hotels.h1.copy(name = "SHOULD NOT WIN")
      val newH2     = HotelsTestData.Hotels.h2

      sc.parallelize(Seq(changedH1, newH2))
        .map(h => (h.id, h.name, h.phone, h.address, h.pois))
        .foreachPartitionCql(
          "INSERT INTO helenus_spark_c3.hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS"
            .toCQL(_)
            .prepare[String, String, String, Address, Set[String]]
        )

      // h1 existed -> not applied -> original name preserved.
      hotelName(HotelsTestData.Hotels.h1.id) shouldBe Some(HotelsTestData.Hotels.h1.name)
      // h2 was new -> applied.
      hotelName(HotelsTestData.Hotels.h2.id) shouldBe Some(HotelsTestData.Hotels.h2.name)
    }

    "apply a conditional IF EXISTS delete" in {
      insertHotel(HotelsTestData.Hotels.h1)
      insertHotel(HotelsTestData.Hotels.h2)

      sc.parallelize(Seq(HotelsTestData.Hotels.h1.id, "does_not_exist"))
        .foreachPartitionCql(
          "DELETE FROM helenus_spark_c3.hotels WHERE id = ? IF EXISTS".toCQL(_).prepare[String]
        )

      hotelName(HotelsTestData.Hotels.h1.id) shouldBe None // deleted
      hotelName(HotelsTestData.Hotels.h2.id) shouldBe Some(
        HotelsTestData.Hotels.h2.name
      ) // untouched
    }

    "apply arbitrary CQL the column-mapping model cannot express (a collection append)" in {
      insertHotel(HotelsTestData.Hotels.h1)

      sc.parallelize(Seq((Set("A Brand New POI"), HotelsTestData.Hotels.h1.id)))
        .foreachPartitionCql(
          "UPDATE helenus_spark_c3.hotels SET pois = pois + ? WHERE id = ?"
            .toCQL(_)
            .prepare[Set[String], String]
        )

      poisContains(HotelsTestData.Hotels.h1.id, "A Brand New POI") shouldBe true
      HotelsTestData.Hotels.h1.pois.foreach { existing =>
        poisContains(HotelsTestData.Hotels.h1.id, existing) shouldBe true
      }
    }

    "prepare one statement per partition, not per record" in {
      PrepareCounter.calls.set(0)
      // Four records across two partitions: per-partition preparation means two prepares,
      // whereas per-record would be four.
      val ids = (1 to 4).map(i => s"p$i")

      sc.parallelize(ids, numSlices = 2)
        .map(id => (id, s"name-$id", "phone", Address.Empty, Set.empty[String]))
        .foreachPartitionCql { session =>
          PrepareCounter.calls.incrementAndGet()
          "INSERT INTO helenus_spark_c3.hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
            .toCQL(session)
            .prepare[String, String, String, Address, Set[String]]
        }

      PrepareCounter.calls.get() shouldBe 2
      ids.foreach(id => hotelName(id) shouldBe Some(s"name-$id"))
    }

    // Ignored, not deleted: the body compiles (so the E2 bridge stays type-checked and its
    // Mapping-serialization contract is exercised at compile time), but it cannot RUN in this
    // module. Any Dataset operation triggers Spark SQL's Catalyst parser, whose ANTLR 4.9.3
    // ATN cannot be read by Helenus core's ANTLR 4.13.2 runtime — and there is no single
    // ANTLR version that satisfies both the compile-time `toCQL` macro (needs 4.13.2) and
    // Spark SQL at runtime (needs 4.9.3). Enabling this requires shading ANTLR inside
    // helenus-core; see the "DataFrame / Dataset" limitation in the module README.
    "write a Dataset[Hotel] through the sink via .prepareFrom" ignore {
      // `prepareFrom[Hotel]` binds via an implicit `Mapping[Hotel]`, which — like any
      // derived mapper — is not serializable. It must be a stable global reference (here a
      // top-level object val) so the builder re-accesses it on the executor rather than
      // capturing the instance into the shipped closure.
      import DatasetSinkFixtures.hotelMapping

      // A Kryo encoder gives a genuine Dataset[Hotel] without needing a Catalyst product
      // encoder for the UDT and Set fields (typed encoders are out of scope for this bridge).
      val ds: Dataset[Hotel] =
        LocalSpark.session.createDataset(
          Seq(HotelsTestData.Hotels.h1, HotelsTestData.Hotels.h2)
        )(Encoders.kryo[Hotel])

      ds.foreachPartitionCql(
        "INSERT INTO helenus_spark_c3.hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
          .toCQL(_)
          .prepareFrom[Hotel]
      )

      hotelName(HotelsTestData.Hotels.h1.id) shouldBe Some(HotelsTestData.Hotels.h1.name)
      hotelName(HotelsTestData.Hotels.h2.id) shouldBe Some(HotelsTestData.Hotels.h2.name)
    }

    "surface a write failure through the configured handler rather than swallowing it" in {
      FailureCounter.count.set(0)
      val config = CassandraSink.Config(
        failureHandler = _ => { FailureCounter.count.incrementAndGet(); () }
      )

      // A null partition key binds fine but is rejected server-side at execute time. The
      // job must fail (the exception is rethrown, not swallowed) and the handler must see it.
      intercept[Exception] {
        sc.parallelize(Seq((null.asInstanceOf[String], "orphan")), numSlices = 1)
          .foreachPartitionCql(
            "INSERT INTO helenus_spark_c3.hotels(id, name) VALUES (?, ?)"
              .toCQL(_)
              .prepare[String, String],
            config
          )
      }

      FailureCounter.count.get() should be >= 1
    }
  }

  private def insertHotel(hotel: Hotel): Unit = {
    val insert =
      "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
        .toCQL(session)
        .prepare[String, String, String, Address, Set[String]]
    session.execute(insert.tupled((hotel.id, hotel.name, hotel.phone, hotel.address, hotel.pois)))
    ()
  }

  private def hotelName(id: String): Option[String] = {
    val row =
      session.execute(SimpleStatement.newInstance("SELECT name FROM hotels WHERE id = ?", id)).one()
    Option(row).map(_.getString("name"))
  }

  private def poisContains(id: String, poi: String): Boolean = {
    val row =
      session.execute(SimpleStatement.newInstance("SELECT pois FROM hotels WHERE id = ?", id)).one()
    row != null && row.getSet("pois", classOf[String]).contains(poi)
  }
}

/** Counts statement preparations, shared in-JVM (executor threads run in the driver JVM on
  * `local[2]`), so the driver can assert the per-partition count after the job.
  */
object PrepareCounter {
  val calls: AtomicInteger = new AtomicInteger(0)
}

/** Counts write failures seen by the sink's failure handler, shared in-JVM like
  * [[PrepareCounter]] so the driver can assert it after the (failed) job.
  */
object FailureCounter {
  val count: AtomicInteger = new AtomicInteger(0)
}

/** Holds the `Mapping[Hotel]` for the E2 `Dataset` sink test as a stable, top-level `val`,
  * so the statement builder re-accesses it on the executor instead of shipping the
  * non-serializable derived mapping captured from a local scope.
  */
object DatasetSinkFixtures {
  implicit val hotelMapping: Mapping[Hotel] = Mapping[Hotel]()
}
