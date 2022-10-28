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

package net.nmoncho.helenus

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt

class MappedAsyncPagingIterableOpsSpec extends AnyWordSpec with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global

  "MappedAsyncPagingIterableOps" should {
    "get current page" in {
      val pi = mockPI
      pi.currPage.toList shouldBe List(1, 2, 3)
    }

    "get next page" in {
      val pi = mockPI

      // Don't remove next line! This call shouldn't be required, but mocks works this way
      pi.currPage.toList shouldBe List(1, 2, 3)
      Await.result(pi.nextPage, 6.seconds).toList shouldBe List(4, 5, 6)
    }

    "concat iterators lazily" in {
      val pi = mockPI

      pi.iter(6.seconds)
        .map { x =>
          println(x)
          x
        }
        .toList shouldBe List(1, 2, 3, 4, 5, 6, 7, 8, 9)
    }
  }

  private def mockPI: MappedAsyncPagingIterable[Int] = {
    import scala.jdk.CollectionConverters._
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    val m = mock(classOf[MappedAsyncPagingIterable[Int]])

    when(m.currentPage())
      .thenReturn(List(1, 2, 3).asJava)
      .thenReturn(List(4, 5, 6).asJava)
      .thenReturn(List(7, 8, 9).asJava)
      .thenReturn(List.empty[Int].asJava)

    when(m.hasMorePages())
      .thenReturn(true)
      .thenReturn(true)
      .thenReturn(false)

    when(m.fetchNextPage()).thenReturn(Future.successful(m).asJava)

    m
  }
}
