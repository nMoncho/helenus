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

package net.nmoncho.helenus.internal.reactive

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.reactivestreams.Subscriber
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EmptyPublisherSpec extends AnyWordSpec with Matchers {

  "EmptyPublisher" should {
    "complete a subscription as soon as it's created" in {
      val published  = new EmptyPublisher[String]
      val subscriber = mock(classOf[Subscriber[String]])

      published.subscribe(subscriber)

      verify(subscriber, atMostOnce()).onComplete()
      verify(subscriber, never()).onNext(any())
      verify(subscriber, never()).onError(any())
      verify(subscriber, never()).onSubscribe(any())
    }

    "require an non-null subscriber" in {
      val published = new EmptyPublisher[String]

      intercept[IllegalArgumentException](
        published.subscribe(null)
      )
    }
  }
}
