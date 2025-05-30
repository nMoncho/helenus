/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package flink.source

import java.util

import scala.collection.immutable.Queue

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.internal.core.metadata.token.TokenFactory
import net.nmoncho.helenus.flink.FlinkCassandraSpec
import net.nmoncho.helenus.flink.models.Hotel
import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner
import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner.Murmur3Partitioner
import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner.RandomPartitioner
import net.nmoncho.helenus.flink.source.CassandraSplit.TokenRange
import net.nmoncho.helenus.utils.HotelsTestData
import org.apache.flink.api.connector.source.ReaderInfo
import org.apache.flink.api.connector.source.SourceOutput
import org.apache.flink.api.connector.source.mocks.MockSplitEnumeratorContext
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CassandraSourceSpec extends AnyWordSpec with Matchers with FlinkCassandraSpec {

  private val parallelism = 2

  "Cassandra Enumerator State" should {
    "add splits back" in {
      val state = createEnumeratorState(Murmur3Partitioner, 4)
      state.splitsToReassign.isEmpty shouldBe true

      val newState = state.addSplitsBack(util.List.of(CassandraSplit(0, 1), CassandraSplit(1, 3)))

      newState.splitsToReassign.isEmpty shouldBe false
    }

    "produce splits" in {
      val state = createEnumeratorState(Murmur3Partitioner, 4)

      // enumerator state starts with empty splits to reassign
      state.splitsToReassign.isEmpty shouldBe true

      val (split1, newState1) = state.nextSplit()
      split1.value shouldBe CassandraSplit(-9223372036854775808L, -4611686018427387904L)

      val (split2, newState2) = newState1.nextSplit()
      split2.value shouldBe CassandraSplit(-4611686018427387904L, 0)

      val (split3, newState3) = newState2.nextSplit()
      split3.value shouldBe CassandraSplit(0, 4611686018427387904L)

      // back splits have priority
      val newState4 = newState3.addSplitsBack(util.List.of(CassandraSplit(0, 4611686018427387904L)))
      val (split5, newState5) = newState4.nextSplit()
      split5.value shouldBe CassandraSplit(0, 4611686018427387904L)

      val (split6, newState6) = newState5.nextSplit()
      split6.value shouldBe CassandraSplit(4611686018427387904L, 9223372036854775807L)

      val (split7, newState7) = newState6.nextSplit()
      split7 shouldBe empty
      newState7.numSplitsLeftToGenerate shouldBe 0
    }
  }

  "Cassandra Split Enumerator" should {
    "prepare splits on start, and then handle split requests" in {
      val context   = new MockSplitEnumeratorContext[CassandraSplit](parallelism)
      val splitEnum = CassandraEnumeratorState.splitEnumerator(
        context,
        None,
        MaxSplitMemorySizeDefault,
        session => "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply(),
        CassandraSource
          .Config()
          .copy(config = cassandraConfig)
      )

      splitEnum.start()
      context.runNextOneTimeCallable()

      withClue("an exception should be thrown if there is no subtask registered") {
        intercept[IllegalStateException](splitEnum.handleSplitRequest(0, "localhost"))
      }

      context.registerReader(new ReaderInfo(0, "localhost"))
      context.getSplitsAssignmentSequence.isEmpty shouldBe true

      splitEnum.handleSplitRequest(0, "localhost")

      context.getSplitsAssignmentSequence.isEmpty shouldBe false
    }
  }

  "Record Emitter" should {
    "collect and map rows" in {
      implicit val cqlSession: CqlSession = session
      val emitter                         = CassandraSourceReader.recordEmitter[Hotel]()
      val output                          = mock(classOf[SourceOutput[Hotel]])

      val rs = "SELECT * FROM hotels".toCQL.prepareUnit.execute()
      rs.iter.foreach { row =>
        emitter.emitRecord(row -> rs.getExecutionInfo, output, CassandraSplit(0, 1))
      }

      verify(output, atLeastOnce()).collect(any())
    }
  }

  "Cassandra Split Reader" should {
    "generate a range query" in {
      CassandraSourceReader.generateRangeQuery(
        "SELECT * FROM hotels",
        "id"
      ) shouldBe "SELECT * FROM hotels WHERE (token(id) >= ?) AND (token(id) < ?)"

      CassandraSourceReader.generateRangeQuery(
        "SELECT * FROM hotels WHERE id = ?",
        "id"
      ) shouldBe "SELECT * FROM hotels WHERE (token(id) >= ?) AND (token(id) < ?) AND id = ?"

      CassandraSourceReader.generateRangeQuery(
        "SELECT * FROM hotels LIMIT 100",
        "id"
      ) shouldBe "SELECT * FROM hotels WHERE (token(id) >= ?) AND (token(id) < ?) LIMIT 100"

      CassandraSourceReader.generateRangeQuery(
        "SELECT * FROM hotels WHERE id = ? LIMIT 100",
        "id"
      ) shouldBe "SELECT * FROM hotels WHERE (token(id) >= ?) AND (token(id) < ?) AND id = ? LIMIT 100"
    }
  }

  "Keyspace and Table" should {
    "be extracted from query" in {
      extractKeyspaceTable(session, "SELECT * FROM hotels") shouldBe (keyspace, "hotels")
      extractKeyspaceTable(session, "SELECT * FROM \"Hotels\"") shouldBe (keyspace, "\"Hotels\"")
      extractKeyspaceTable(session, s"SELECT * FROM $keyspace.hotels") shouldBe (keyspace, "hotels")

      extractKeyspaceTable(
        session,
        "SELECT * FROM \"some_Keyspace\".hotels"
      ) shouldBe ("\"some_Keyspace\"", "hotels")
    }
  }

  "CassandraSplit Generator" should {
    "calculate the ring fraction and estimate table size" in {
      val generator = new CassandraSplit.Generator(
        Murmur3Partitioner,
        session,
        keyspace,
        "hotels",
        parallelism,
        MaxSplitMemorySizeDefault
      )

      val ranges = List(
        TokenRange(3440, 7819, BigInt("-1155248203254640778"), BigInt("623235028026430550")),
        TokenRange(360, 7819, BigInt("-2247919833322265667"), BigInt("-2067072050999155035")),
        TokenRange(196, 7819, BigInt("-9223372036854775808"), BigInt("-9122919794564684744")),
        TokenRange(480, 7819, BigInt("1318952385700065821"), BigInt("1518111753146867368")),
        TokenRange(2316, 7819, BigInt("1842790004171971686"), BigInt("2948339300865289673")),
        TokenRange(1158, 7819, BigInt("6600169184456980210"), BigInt("7184503550510532093")),
        TokenRange(965, 7819, BigInt("7463162456341248922"), BigInt("7935912419927393669")),
        TokenRange(1497, 7819, BigInt("7935912419927393669"), BigInt("8658184851358368868")),
        TokenRange(787, 7819, BigInt("8816956601248912202"), BigInt("-9223372036854775808"))
      )

      generator.ringFraction(ranges).doubleValue shouldBe (0.30088043 +- 0.1)
      generator.estimateTableSize(ranges) shouldBe 291029184
      generator.decideOnNumSplits(291029184) shouldBe 4 // = (size / MaxSplitMemorySize)

      val state = generator.prepareSplit(4)
      state shouldBe CassandraEnumeratorState(
        numSplitsLeftToGenerate = 4,
        increment               = BigInt("4611686018427387904"),
        startToken              = BigInt("-9223372036854775808"),
        maxToken                = BigInt("9223372036854775807"),
        splitsToReassign        = Queue.empty
      )

      withClue("even when there are no stats ready") {
        generator.ringFraction(List.empty) shouldBe 0
        generator.estimateTableSize() shouldBe 0
        generator.decideOnNumSplits() shouldBe parallelism
        generator.prepareSplit() shouldBe CassandraEnumeratorState(
          numSplitsLeftToGenerate = 2,
          increment               = BigInt("9223372036854775808"),
          startToken              = BigInt("-9223372036854775808"),
          maxToken                = BigInt("9223372036854775807"),
          splitsToReassign        = Queue.empty
        )
      }
    }
  }

  "CassandraPartitioner" should {
    "be created from session" in {
      CassandraPartitioner(session) shouldBe Murmur3Partitioner
    }

    "be created from `system.local` table" in {
      CassandraPartitioner(None, session) shouldBe Murmur3Partitioner
    }

    "provide a token factory" in {
      Murmur3Partitioner.factory shouldBe a[TokenFactory]
      RandomPartitioner.factory shouldBe a[TokenFactory]
    }
  }

  private def createEnumeratorState(
      partitioner: CassandraPartitioner,
      splitsToGenerate: Long
  ): CassandraEnumeratorState =
    CassandraEnumeratorState(
      numSplitsLeftToGenerate = splitsToGenerate,
      increment               = partitioner.ringSize / splitsToGenerate,
      startToken              = partitioner.minToken,
      maxToken                = partitioner.maxToken,
      splitsToReassign        = Queue.empty
    )

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeFile("hotels.cql")
    HotelsTestData.insertTestData()(session)
  }

  override def afterEach(): Unit = () // don't truncate keyspace
}
