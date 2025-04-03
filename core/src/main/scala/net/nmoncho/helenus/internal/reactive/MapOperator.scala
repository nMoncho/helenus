/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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
