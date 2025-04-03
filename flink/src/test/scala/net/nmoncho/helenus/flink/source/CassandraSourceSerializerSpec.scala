/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.source

import scala.collection.immutable.Queue

import net.nmoncho.helenus.flink.source.CassandraSplit.CassandraPartitioner._
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class CassandraSourceSerializerSpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers {

  private val successful = minSuccessful(500)

  property("CassandraSplit serialization should round-trip") {
    val serializer = CassandraSplit.serializer

    forAll(cassandraSplitGen, successful) { split =>
      serializer.deserialize(CassandraSplit.serializerVersion, serializer.serialize(split))
    }
  }

  property("CassandraEnumeratorState serialization should round-trip") {
    val serializer = CassandraEnumeratorState.Serializer

    forAll(enumeratorStateGen, successful) { enumState =>
      serializer.deserialize(
        CassandraEnumeratorState.SerializerVersion,
        serializer.serialize(enumState)
      )
    }
  }

  private def cassandraSplitGen: Gen[CassandraSplit] = for {
    start <- Gen.choose(Murmur3Partitioner.minToken, Murmur3Partitioner.maxToken)
    end <- Gen.choose(Murmur3Partitioner.minToken, Murmur3Partitioner.maxToken)
  } yield CassandraSplit(start, end)

  private def enumeratorStateGen: Gen[CassandraEnumeratorState] = for {
    numSplitsLeftToGenerate <- Gen.choose(0L, Long.MaxValue)
    increment <- Gen.choose(Murmur3Partitioner.minToken, RandomPartitioner.maxToken)
    startToken <- Gen.choose(Murmur3Partitioner.minToken, RandomPartitioner.maxToken)
    maxToken <- Gen.choose(Murmur3Partitioner.minToken, RandomPartitioner.maxToken)
    splits <- Gen.listOf(cassandraSplitGen)
  } yield CassandraEnumeratorState(
    numSplitsLeftToGenerate,
    increment,
    startToken,
    maxToken,
    Queue(splits: _*)
  )
}
