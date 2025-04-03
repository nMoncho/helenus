/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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

    // $COVERAGE-OFF$
    override def onError(t: Throwable): Unit =
      subscriber.onError(t)
    // $COVERAGE-ON$

    override def onComplete(): Unit =
      subscriber.onComplete()
  }
}
