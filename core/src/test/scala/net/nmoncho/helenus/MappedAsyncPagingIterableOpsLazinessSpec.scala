/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import java.util.concurrent.CompletableFuture

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `iter` must fetch the next page only after the current one is exhausted, so a
  * partial consumption does not trigger network round-trips (and page decoding)
  * for later pages. This used to differ between Scala 2.12 (eager) and 2.13
  * (lazy); it is now lazy on both.
  */
class MappedAsyncPagingIterableOpsLazinessSpec extends AnyWordSpec with Matchers {

  "iter" should {
    "fetch the next page only after the current one is exhausted" in {
      val page2 = mock(classOf[MappedAsyncPagingIterable[String]])
      when(page2.currentPage()).thenReturn(List("c").asJava)
      when(page2.hasMorePages).thenReturn(false)

      val page1 = mock(classOf[MappedAsyncPagingIterable[String]])
      when(page1.currentPage()).thenReturn(List("a", "b").asJava)
      when(page1.hasMorePages).thenReturn(true)
      when(page1.fetchNextPage()).thenReturn(CompletableFuture.completedFuture(page2))

      val it = page1.iter(5.seconds)

      it.next() shouldBe "a"
      it.next() shouldBe "b"
      // "a" and "b" came from the already-loaded first page: no fetch yet.
      verify(page1, never()).fetchNextPage()

      it.next() shouldBe "c" // crossing the page boundary triggers exactly one fetch
      verify(page1, times(1)).fetchNextPage()

      it.hasNext shouldBe false
    }
  }
}
