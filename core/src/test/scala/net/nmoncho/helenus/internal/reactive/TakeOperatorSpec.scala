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
