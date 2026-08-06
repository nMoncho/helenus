/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import java.nio.ByteBuffer

import scala.util.Failure
import scala.util.Success

import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.api.core.cql.Statement
import com.datastax.oss.driver.api.core.session.Session
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for the [[PagerSerializer]] implementations.
  *
  * `serialize`/`deserialize` are close to pure functions, so these don't need a
  * live Cassandra connection: `serialize` reads off a stub [[PagingState]] and
  * `deserialize` ignores the bound statement. The end-to-end paging round-trip
  * (encoding a genuine driver paging state mid-query and continuing from it)
  * lives in `PagerSerializerCassandraSpec`.
  */
class PagerSerializerSpec extends AnyWordSpec with Matchers {

  // `deserialize` ignores the bound statement for both serializers
  private val noBoundStatement: ScalaBoundStatement[_] = null

  "DefaultPagingStateSerializer" should {
    val serializer = PagerSerializer.DefaultPagingStateSerializer

    "serialize a paging state to its string form" in {
      val pagingState = stubPagingState(asString = "some-encoded-state")

      serializer.serialize(pagingState) shouldBe Success("some-encoded-state")
    }

    "fail to deserialize a tampered state" in {
      val result = serializer.deserialize(noBoundStatement, "29a1f5e96cfd7e7f42fbb3b3092a")

      result match {
        case Success(value) =>
          fail(s"Expected an invalid state here instead of $value")

        case Failure(exception) =>
          exception shouldBe a[IllegalArgumentException]
          exception.getMessage should include(
            "Cannot deserialize paging state, invalid format. The serialized form was corrupted, or not initially generated from a PagingState object"
          )
      }
    }
  }

  "SimplePagingStateSerializer" should {
    val serializer = PagerSerializer.SimplePagingStateSerializer

    "serialize a paging state to its raw ByteBuffer" in {
      val raw         = ByteBuffer.wrap("raw-state".getBytes())
      val pagingState = stubPagingState(rawState = raw)

      serializer.serialize(pagingState) shouldBe Success(raw)
    }

    "deserialize the raw ByteBuffer back into a paging state" in {
      val raw = ByteBuffer.wrap("raw-state".getBytes())

      serializer.deserialize(noBoundStatement, raw) match {
        case Success(pagingState) =>
          pagingState.getRawPagingState shouldBe raw
          pagingState.toBytes shouldBe raw.array()

        case Failure(exception) =>
          fail("Expected a valid paging state", exception)
      }
    }

    "reuse a state regardless of the statement it came from (it never validates)" in {
      // This is the defining, deliberately unsafe behaviour of this serializer:
      // the deserialized state matches ANY statement/session.
      val raw = ByteBuffer.wrap("raw-state".getBytes())

      serializer.deserialize(noBoundStatement, raw) match {
        case Success(pagingState) =>
          pagingState.matches(anyStatement, anySession) shouldBe true

        case Failure(exception) =>
          fail("Expected a valid paging state", exception)
      }
    }
  }

  /** Minimal [[PagingState]] stub whose accessors return the given values. */
  private def stubPagingState(
      asString: String     = "",
      rawState: ByteBuffer = ByteBuffer.allocate(0)
  ): PagingState = new PagingState {
    override def toString: String                                            = asString
    override def getRawPagingState: ByteBuffer                               = rawState
    override def toBytes: Array[Byte]                                        = rawState.array()
    override def matches(statement: Statement[_], session: Session): Boolean = false
  }

  private def anyStatement: Statement[_] = null
  private def anySession: Session        = null
}
