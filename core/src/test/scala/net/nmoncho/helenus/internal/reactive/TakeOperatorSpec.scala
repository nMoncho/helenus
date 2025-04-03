/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.reactive

import java.util.concurrent.SubmissionPublisher

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
  }
}
