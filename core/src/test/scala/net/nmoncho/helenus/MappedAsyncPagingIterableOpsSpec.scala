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

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MappedAsyncPagingIterableOpsSpec extends AnyWordSpec with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global

  private val timeout = 6.seconds

  "MappedAsyncPagingIterableOps" should {
    "get current page" in {
      val pi = mockPI()
      pi.currPage.toList shouldBe List(1, 2, 3)
    }

    "get nextOption" in {
      val pi = mockPI()

      pi.nextOption(timeout) shouldBe Some(1)
      pi.nextOption(timeout) shouldBe Some(2)
      pi.nextOption(timeout) shouldBe Some(3)
      // next page
      pi.nextOption(timeout) shouldBe Some(4)
    }

    "get next page" in {
      val pi = mockPI()

      // Don't remove next line! This call shouldn't be required, but mocks works this way
      pi.currPage.toList shouldBe List(1, 2, 3)
      Await.result(pi.nextPage, timeout).toList shouldBe List(4, 5, 6)
    }

    "concat iterators lazily" in {
      val pi = mockPI()

      pi.iter(timeout)
        .map { x =>
          println(x)
          x
        }
        .toList shouldBe List(1, 2, 3, 4, 5, 6, 7, 8, 9)
    }
  }

  private def mockPI(): MappedAsyncPagingIterable[java.lang.Integer] = {
    import scala.jdk.CollectionConverters._
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    val m = mock(classOf[MappedAsyncPagingIterable[java.lang.Integer]])

    when(m.currentPage())
      .thenReturn(List(1: java.lang.Integer, 2: java.lang.Integer, 3: java.lang.Integer).asJava)
      .thenReturn(List(4: java.lang.Integer, 5: java.lang.Integer, 6: java.lang.Integer).asJava)
      .thenReturn(List(7: java.lang.Integer, 8: java.lang.Integer, 9: java.lang.Integer).asJava)
      .thenReturn(List.empty[java.lang.Integer].asJava)

    when(m.hasMorePages())
      .thenReturn(true)
      .thenReturn(true)
      .thenReturn(false)

    when(m.fetchNextPage()).thenReturn(Future.successful(m).asJava)

    when(m.one())
      .thenReturn(1)
      .thenReturn(2)
      .thenReturn(3)
      .thenReturn(null: java.lang.Integer)
      .thenReturn(4)

    m
  }
}
