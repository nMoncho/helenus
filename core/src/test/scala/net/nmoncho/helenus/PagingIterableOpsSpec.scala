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

import scala.annotation.nowarn

import com.datastax.oss.driver.api.core.PagingIterable
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@nowarn("cat=unused-imports")
class PagingIterableOpsSpec extends AnyWordSpec with Matchers {
  import scala.collection.compat._ // Don't remove me

  "PagingIterableOps" should {
    "provide 'headOption'" in {
      val nonEmpty = mockPagingIterable(List(1, 2, 3, 4, 5))
      nonEmpty.nextOption() shouldBe Some(1)

      val empty = mockPagingIterable(List.empty[Int])
      empty.nextOption() shouldBe None
    }

    "provide a Scala Iterator" in {
      val pagingIterable = mockPagingIterable(List(1, 2, 3, 4, 5))

      pagingIterable.iter shouldBe a[scala.collection.Iterator[_]]
    }

    "convert to a collection" in {
      def pagingIterable = mockPagingIterable(List(1, 2, 3, 4, 5))

      pagingIterable.to(List) shouldBe List(1, 2, 3, 4, 5)
      pagingIterable.to(Vector) shouldBe Vector(1, 2, 3, 4, 5)
      pagingIterable.to(Set) shouldBe Set(1, 2, 3, 4, 5)
    }
  }

  private def mockPagingIterable[T](iterable: Iterable[T]): PagingIterable[T] = {
    import scala.jdk.CollectionConverters._

    val pi = mock(classOf[PagingIterable[T]])

    when(pi.one()).thenCallRealMethod()
    when(pi.iterator()).thenReturn(iterable.iterator.asJava)

    pi
  }
}
