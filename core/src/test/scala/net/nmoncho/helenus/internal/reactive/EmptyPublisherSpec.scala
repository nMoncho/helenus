/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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
