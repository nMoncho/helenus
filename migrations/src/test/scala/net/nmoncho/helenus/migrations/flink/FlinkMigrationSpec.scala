/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.flink

import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.nmoncho.helenus._
import net.nmoncho.helenus.flink._
import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._
import net.nmoncho.helenus.utils.CassandraSpec
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B6: a Cassandra to Cassandra migration runs as a Flink job, reading through the
  * token-range `CassandraSource` (from helenus-flink) and writing through the
  * Cassandra sink. The source's checkpointed split-enumerator state is what lets a
  * Flink savepoint resume without re-reading finished splits (mechanism covered by
  * the helenus-flink source and serializer specs).
  */
class FlinkMigrationSpec
    extends AnyWordSpec
    with Matchers
    with CassandraSpec
    with BeforeAndAfterEach {

  import FlinkMigrationSpec.Migrated

  private val source = "b6_source"
  private val target = "b6_target"
  private val total  = 150

  private lazy val flinkCluster = new MiniClusterWithClientResource(
    new MiniClusterResourceConfiguration.Builder()
      .setNumberSlotsPerTaskManager(2)
      .setNumberTaskManagers(1)
      .build
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(s"CREATE TABLE IF NOT EXISTS $source (id int PRIMARY KEY, v text)")
    executeDDL(s"CREATE TABLE IF NOT EXISTS $target (id int PRIMARY KEY, v text)")
  }

  // `CassandraSpec.afterEach` truncates the keyspace, so re-seed the source each test.
  override def beforeEach(): Unit = {
    super.beforeEach()
    flinkCluster.before()
    (1 to total).foreach(i => execute(s"INSERT INTO $source (id, v) VALUES ($i, 'v$i')"))
  }

  override def afterEach(): Unit =
    try flinkCluster.after()
    finally super.afterEach()

  "A Flink Cassandra to Cassandra migration" should {

    "read every row via the token-range source and load it into the target" in {
      // Bind table names and config to locals so the source/sink builders do not
      // capture the (non-serializable) test instance in the Flink job graph.
      val sourceTable  = source
      val targetTable  = target
      val driverConfig = cassandraConfig

      val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)

      val read = (s: CqlSession) =>
        s"SELECT id, v FROM $sourceTable".toUnsafeCQL(s).prepareUnit.as[Migrated].apply()

      val stream: DataStream[Migrated] = env.fromSource(
        read.asSource(CassandraSource.Config().copy(config = driverConfig)),
        WatermarkStrategy.noWatermarks(),
        "cassandra-token-range-source"
      )

      stream.addCassandraSink(
        (s: CqlSession) =>
          s"INSERT INTO $targetTable (id, v) VALUES (?, ?)"
            .toUnsafeCQL(s)
            .prepare[Int, String]
            .from[Migrated],
        CassandraSink.Config().copy(config = driverConfig)
      )

      env.execute()

      val migrated = execute(s"SELECT id, v FROM $target")
        .iterator()
        .asScala
        .map(row => row.getInt("id") -> row.getString("v"))
        .toMap

      migrated shouldBe (1 to total).map(i => i -> s"v$i").toMap
    }

    "run as one composed migration via asTokenRangeMigration (C1)" in {
      val sourceTable  = source
      val targetTable  = target
      val driverConfig = cassandraConfig

      val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)

      asTokenRangeMigration(
        env,
        read = (s: CqlSession) =>
          s"SELECT id, v FROM $sourceTable".toUnsafeCQL(s).prepareUnit.as[Migrated].apply(),
        transform = (migrated: Migrated) => migrated,
        write     = (s: CqlSession) =>
          s"INSERT INTO $targetTable (id, v) VALUES (?, ?)"
            .toUnsafeCQL(s)
            .prepare[Int, String]
            .from[Migrated],
        sourceConfig = CassandraSource.Config().copy(config = driverConfig),
        sinkConfig   = CassandraSink.Config().copy(config = driverConfig)
      )

      env.execute()

      val migrated = execute(s"SELECT id, v FROM $target")
        .iterator()
        .asScala
        .map(row => row.getInt("id") -> row.getString("v"))
        .toMap

      migrated shouldBe (1 to total).map(i => i -> s"v$i").toMap
    }
  }

  private def cassandraConfig: Config = ConfigFactory
    .parseString(s"""
                    |datastax-java-driver.basic {
                    |  contact-points = ["$contactPoint"]
                    |  session-keyspace = "$keyspace"
                    |  load-balancing-policy.local-datacenter = "datacenter1"
                    |}""".stripMargin)
    .withFallback(ConfigFactory.load())
}

object FlinkMigrationSpec {

  final case class Migrated(id: Int, v: String) {
    def this() = this(0, "")
  }

  object Migrated {
    // Hand-written so the mapper captures no codec objects as fields: Flink serializes
    // the source's RowMapper, and the core codecs (for example IntCodec) are not
    // Serializable. Codecs here are resolved inside `apply`, not stored.
    implicit val rowMapper: RowMapper[Migrated] = new RowMapper[Migrated] {
      override def apply(row: Row): Migrated =
        Migrated(row.getCol[Int]("id"), row.getCol[String]("v"))
    }

    implicit val adapter: Adapter[Migrated, (Int, String)] = Adapter[Migrated]
    implicit val typeInfo: TypeInformation[Migrated]       = Pojo[Migrated]
  }
}
