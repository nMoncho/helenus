/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package bench
package reactive

import org.openjdk.jmh.infra.Blackhole
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

/** A cold, synchronous [[Publisher]] emitting `0 until n`.
  *
  * On the first `request`, it pushes elements inline (honouring demand and cancellation) and then
  * completes. Being synchronous keeps the reactive-operator benchmarks deterministic, with no
  * scheduler or timing noise, so they measure only the operator's per-element overhead.
  */
final class SyncRangePublisher(n: Int) extends Publisher[Integer] {
  override def subscribe(sub: Subscriber[_ >: Integer]): Unit =
    sub.onSubscribe(new Subscription {
      private var i         = 0
      private var cancelled = false

      override def request(count: Long): Unit = {
        var remaining = count
        while (!cancelled && remaining > 0 && i < n) {
          val value = Integer.valueOf(i)
          i += 1
          remaining -= 1
          sub.onNext(value)
        }
        if (!cancelled && i >= n) sub.onComplete()
      }

      override def cancel(): Unit = cancelled = true
    })
}

/** Terminal subscriber that requests everything up front and feeds each element to the blackhole. */
final class CountingSubscriber[A](blackHole: Blackhole) extends Subscriber[A] {
  override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)
  override def onNext(t: A): Unit                 = blackHole.consume(t)
  override def onError(t: Throwable): Unit        = ()
  override def onComplete(): Unit                 = ()
}
