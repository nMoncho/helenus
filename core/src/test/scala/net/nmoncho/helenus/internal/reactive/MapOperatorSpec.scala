/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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

    "propagate errors" in {
      val publisher = new SubmissionPublisher[Int]()
      val op = new MapOperator(
        FlowAdapters.toPublisher(publisher),
        (s: Int) => s / s
      )

      val subscriber = new EndSubscriber[Int]()
      op.publisher.subscribe(
        FlowAdapters.toSubscriber(subscriber)
      )

      val items = List(0, 1, 2)
      items.foreach(publisher.submit)
      publisher.close()

      eventually {
        subscriber.elems.toList shouldBe empty
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

  class EndSubscriber[T]() extends Flow.Subscriber[T] {
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
