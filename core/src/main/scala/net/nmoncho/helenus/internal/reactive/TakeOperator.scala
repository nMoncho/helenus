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
    private var count                      = 0
    private var completed                  = false
    private var subscription: Subscription = _

    override def onSubscribe(s: Subscription): Unit = {
      // Keep the upstream subscription so we can cancel it once we have enough
      // elements, otherwise the driver keeps auto-fetching (and decoding) pages
      // we would only discard.
      subscription = s
      subscriber.onSubscribe(s)
    }

    override def onNext(t: A): Unit =
      // Reactive Streams delivers signals serially, so no synchronization is
      // needed here. Ignore any element that arrives in-flight after we have
      // already completed (e.g. before the upstream cancellation takes effect).
      if (!completed) {
        if (count < amount) {
          subscriber.onNext(t)
          count += 1
        }

        if (count >= amount) {
          complete()
        }
      }

    // $COVERAGE-OFF$
    override def onError(t: Throwable): Unit =
      if (!completed) {
        completed = true
        subscriber.onError(t)
      }
    // $COVERAGE-ON$

    override def onComplete(): Unit =
      if (!completed) {
        completed = true
        subscriber.onComplete()
      }

    /** Cancel the upstream subscription and complete the downstream, at most once. */
    private def complete(): Unit =
      if (!completed) {
        completed = true
        if (subscription != null) subscription.cancel()
        subscriber.onComplete()
      }
  }
}
