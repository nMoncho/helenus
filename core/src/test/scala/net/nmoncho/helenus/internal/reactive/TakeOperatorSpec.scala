/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.reactive

import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.mutable.ListBuffer

import net.nmoncho.helenus.internal.reactive.MapOperatorSpec.EndSubscriber
import org.reactivestreams.FlowAdapters
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TakeOperatorSpec extends AnyWordSpec with Matchers with Eventually {

  "TakeOperator" should {
    val amount = 2

    "take only amount of events" in {
      val publisher = new SubmissionPublisher[String]()
      val op        = new TakeOperator[String](FlowAdapters.toPublisher(publisher), amount)

      val subscriber = new EndSubscriber[String]()
      op.publisher.subscribe(
        FlowAdapters.toSubscriber(subscriber)
      )

      val items = List("1", "2", "3", "4", "5")
      items.foreach(publisher.submit)
      publisher.close()

      eventually {
        subscriber.elems.toList should have size amount
      }
    }

    "cancel the upstream and complete exactly once when the limit is reached" in {
      val publisher = new SubmissionPublisher[String]()
      val op        = new TakeOperator[String](FlowAdapters.toPublisher(publisher), amount)

      val subscriber = new CountingSubscriber[String]()
      op.publisher.subscribe(
        FlowAdapters.toSubscriber(subscriber)
      )

      // Submit more than `amount`. The publisher is intentionally NOT closed, so
      // the only source of onComplete is the take-limit itself. Before the fix
      // the upstream was never cancelled and every surplus element re-signalled
      // onComplete.
      List("1", "2", "3", "4", "5").foreach(publisher.submit)

      eventually {
        subscriber.elems.toList should have size amount
        subscriber.completions.get() shouldBe 1
        // A cancelled subscription is removed from the SubmissionPublisher.
        publisher.getNumberOfSubscribers shouldBe 0
      }
    }
  }

  /** Records elements and counts terminal `onComplete` signals; requests
    * everything up front so surplus elements would flow if not cancelled.
    */
  private class CountingSubscriber[T] extends Flow.Subscriber[T] {
    val elems: ListBuffer[T]       = new ListBuffer[T]()
    val completions: AtomicInteger = new AtomicInteger(0)

    override def onSubscribe(s: Flow.Subscription): Unit = s.request(Long.MaxValue)

    override def onNext(t: T): Unit = synchronized(elems += t)

    override def onError(t: Throwable): Unit = ()

    override def onComplete(): Unit = { completions.incrementAndGet(); () }
  }
}
