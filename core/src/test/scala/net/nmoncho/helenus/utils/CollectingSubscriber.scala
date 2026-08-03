/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.utils

import java.util.concurrent.Flow

import scala.collection.mutable.ListBuffer

/** A `Flow.Subscriber` that eagerly requests every element and records what it
  * receives, along with terminal completion/error signals.
  *
  * Handy for draining a bounded reactive `Publisher` in tests: subscribe, wait
  * until `completed` is `true` (e.g. with `eventually`), then inspect `elems`.
  */
final class CollectingSubscriber[T] extends Flow.Subscriber[T] {

  val elems: ListBuffer[T] = ListBuffer.empty[T]

  @volatile var completed: Boolean       = false
  @volatile var error: Option[Throwable] = None

  override def onSubscribe(subscription: Flow.Subscription): Unit =
    subscription.request(Long.MaxValue)

  override def onNext(t: T): Unit = {
    elems += t
    ()
  }

  override def onError(t: Throwable): Unit = {
    error     = Some(t)
    completed = true
  }

  override def onComplete(): Unit =
    completed = true
}
