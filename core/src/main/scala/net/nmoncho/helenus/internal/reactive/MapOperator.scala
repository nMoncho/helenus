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

/** Implements a map operation on [[Publisher]]s
  *
  * @param pub original publisher, with elements to be adapted
  * @param mapper how to map origin elements into target elements
  * @tparam A origin type
  * @tparam B target type
  */
class MapOperator[A, B](pub: Publisher[A], mapper: A => B) {

  /** Target type [[Publisher]]
    */
  def publisher: Publisher[B] = new Publisher[B] {
    override def subscribe(s: Subscriber[_ >: B]): Unit = {
      // We need to delay the mapping subscriber creation until we have
      // the downstream subscription, this way we can delegate all calls
      val map = new MapSubscriber(s)
      pub.subscribe(map)
    }
  }

  private class MapSubscriber[C >: B](subscriber: Subscriber[C]) extends Subscriber[A] {
    override def onSubscribe(s: Subscription): Unit =
      subscriber.onSubscribe(s)

    override def onNext(t: A): Unit =
      subscriber.onNext(mapper(t))

    override def onError(t: Throwable): Unit =
      subscriber.onError(t)

    override def onComplete(): Unit =
      subscriber.onComplete()
  }

}
