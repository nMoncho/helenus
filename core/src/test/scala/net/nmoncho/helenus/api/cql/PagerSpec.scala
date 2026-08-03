/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package api.cql

import java.nio.ByteBuffer

import scala.util.Failure
import scala.util.Try

import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.api.core.cql.Statement
import com.datastax.oss.driver.api.core.session.Session
import net.nmoncho.helenus.internal.cql.{ Pager => InternalPager }
import net.nmoncho.helenus.models.Hotel
import net.nmoncho.helenus.utils.CollectingSubscriber
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito._
import org.reactivestreams.FlowAdapters
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

/** Unit tests for the branches of [[InternalPager]] that don't need a live
  * Cassandra connection: encoding failures and the no-more-pages short-circuit.
  * The paging round-trips against a real result set live in
  * `PagerCassandraSpec`.
  */
class PagerSpec extends AnyWordSpec with Matchers with Eventually {

  import Hotel.rowMapper

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "Pager.encodePagingState" should {
    "return None when the serializer fails to serialize the state" in {
      // A bound statement is only touched to log the failing query.
      val bstmt = mock(classOf[ScalaBoundStatement[Hotel]], RETURNS_DEEP_STUBS)
      when(bstmt.getPreparedStatement.getQuery).thenReturn("SELECT * FROM hotels")

      val pager = new InternalPager[Hotel](bstmt, Some(stubPagingState), hasMorePages = true)

      pager.encodePagingState(failingSerializer) shouldBe None
    }
  }

  "Pager.executeReactive" should {
    "emit nothing when there are no more pages" in {
      // `bstmt`/`session` are never touched on the no-more-pages path.
      val pager      = new InternalPager[Hotel](null, None, hasMorePages = false)
      val subscriber = new CollectingSubscriber[(Pager[Hotel], Hotel)]

      pager.executeReactive(2)(null).subscribe(FlowAdapters.toSubscriber(subscriber))

      eventually {
        subscriber.completed shouldBe true
      }
      subscriber.error shouldBe empty
      subscriber.elems shouldBe empty
    }
  }

  private def stubPagingState: PagingState = new PagingState {
    override def toBytes: Array[Byte]                                        = Array.emptyByteArray
    override def matches(statement: Statement[_], session: Session): Boolean = true
    override def getRawPagingState: ByteBuffer                               = ByteBuffer.allocate(0)
  }

  private val failingSerializer: PagerSerializer[String] = new PagerSerializer[String] {
    override def serialize(pagingState: PagingState): Try[String] =
      Failure(new RuntimeException("boom"))

    override def deserialize(bstmt: ScalaBoundStatement[_], value: String): Try[PagingState] =
      Failure(new RuntimeException("not used"))
  }
}
