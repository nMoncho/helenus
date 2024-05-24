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
