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

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/** Provides a [[Publisher]] that will publish at most [[amount]] elements
  *
  * @param pub original published where elements are coming from
  * @param amount at most amount of elements to publish
  * @tparam A published element type
  */
class TakeOperator[A](pub: Publisher[A], amount: Int) {

  def publisher: Publisher[A] = new Publisher[A] {
    override def subscribe(s: Subscriber[_ >: A]): Unit = {
      // We need to delay the mapping subscriber creation until we have
      // the downstream subscription, this way we can delegate all calls
      val takeOp = new TakeSubscriber(s)
      pub.subscribe(takeOp)
    }
  }

  private class TakeSubscriber[B >: A](subscriber: Subscriber[B]) extends Subscriber[A] {
    private var count = 0

    override def onSubscribe(s: Subscription): Unit =
      subscriber.onSubscribe(s)

    override def onNext(t: A): Unit = {
      if (count < amount) {
        subscriber.onNext(t)
      }

      count += 1
      if (count >= amount) {
        subscriber.onComplete()
      }
    }

    override def onError(t: Throwable): Unit =
      subscriber.onError(t)

    override def onComplete(): Unit =
      subscriber.onComplete()
  }
}
