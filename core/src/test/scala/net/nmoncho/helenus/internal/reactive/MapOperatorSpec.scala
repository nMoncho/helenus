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

import java.util.concurrent.Flow
import java.util.concurrent.Flow.Subscription
import java.util.concurrent.SubmissionPublisher

import scala.collection.mutable.ListBuffer

import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.utils.CassandraSpec
import org.reactivestreams.FlowAdapters
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpec

class MapOperatorSpec extends AnyWordSpec with Matchers with Eventually with CassandraSpec {
  import MapOperatorSpec._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(6, Seconds))

  "MapOperator" should {
    "map elements from publisher" in {
      val publisher = new SubmissionPublisher[String]()
      val op = new MapOperator(
        FlowAdapters.toPublisher(publisher),
        (s: String) => s.toInt
      )

      val subscriber = new EndSubscriber[Int]()
      op.publisher.subscribe(
        FlowAdapters.toSubscriber(subscriber)
      )

      val expected = List(1, 2, 3)
      val items    = List("1", "2", "3")
      items.foreach(publisher.submit)
      publisher.close()

      eventually {
        subscriber.elems.toList shouldBe expected
      }
    }
  }

  "ReactiveResultSet" should {
    import net.nmoncho.helenus._

    val ijes = List(
      IceCream("vanilla", numCherries    = 2, cone  = true),
      IceCream("chocolate", numCherries  = 0, cone  = false),
      IceCream("the answer", numCherries = 42, cone = true)
    )

    "be mapped properly" in {
      ijes.foreach(ice =>
        execute(
          s"INSERT INTO ice_creams(name, numCherries, cone) VALUES ('${ice.name}', ${ice.numCherries}, ${ice.cone})"
        )
      )

      val rrs        = session.executeReactive("SELECT * FROM ice_creams")
      val publisher  = rrs.as[IceCream]
      val subscriber = new EndSubscriber[IceCream]()
      publisher.subscribe(
        FlowAdapters.toSubscriber(subscriber)
      )

      eventually {
        subscriber.elems.toSet shouldBe ijes.toSet
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL("""CREATE TABLE IF NOT EXISTS ice_creams(
        |  name         TEXT PRIMARY KEY,
        |  numCherries  INT,
        |  cone         BOOLEAN
        |)""".stripMargin)
  }
}

object MapOperatorSpec {

  case class IceCream(name: String, numCherries: Int, cone: Boolean)
  object IceCream {
    import net.nmoncho.helenus._
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
  }

  private class EndSubscriber[T]() extends Flow.Subscriber[T] {
    private var subscription: Subscription = _
    val elems: ListBuffer[T]               = new ListBuffer[T]()

    override def onSubscribe(s: Subscription): Unit = {
      this.subscription = s
      this.subscription.request(1)
    }

    override def onNext(t: T): Unit = {
      elems += t
      this.subscription.request(1)
    }

    override def onError(t: Throwable): Unit =
      t.printStackTrace()

    override def onComplete(): Unit = ()
  }
}
