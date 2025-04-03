/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.reactive

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

/** [[Publisher]] implementation that doesn't publish any element
  *
  * @tparam A published element type
  */
class EmptyPublisher[A] extends Publisher[A] {
  override def subscribe(s: Subscriber[_ >: A]): Unit = {
    require(s != null, "Subscriber must not be null")
    s.onComplete()
  }
}
