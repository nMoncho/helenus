package net.nmoncho.helenus

import _root_.monix.eval.Task
import _root_.monix.execution.Ack
import _root_.monix.reactive.{ Consumer, Observable, Observer }
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ BatchStatement, BatchType }
import net.nmoncho.helenus.api.cql.{ Pager, ScalaPreparedStatement, WrappedBoundStatement }
import net.nmoncho.helenus.internal.compat.FutureConverters.CompletionStageOps
import net.nmoncho.helenus.internal.cql._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

package object monix {

  implicit class WrappedBoundStatementOps[Out](
      private val wbs: WrappedBoundStatement[Out]
  ) extends AnyVal {

    /** An [[Observable]] reading from Cassandra */
    def asObservable()(implicit session: CqlSession): Observable[Out] =
      Observable.fromReactivePublisher(wbs.executeReactive())

  }

  implicit class ScalaPreparedStatementUnitOps[Out](
      private val pstmt: ScalaPreparedStatementUnit[Out]
  ) extends AnyVal {

    /** An [[Observable]] reading from Cassandra */
    def asObservable()(
        implicit session: CqlSession
    ): Observable[Out] =
      Observable.fromReactivePublisher(pstmt.executeReactive())

  }

  implicit class ScalaPreparedStatement1Ops[T1, Out](
      private val pstmt: ScalaPreparedStatement1[T1, Out]
  ) extends AnyVal {
    def asObservable(t1: T1)(implicit session: CqlSession): Observable[Out] =
      Observable.fromReactivePublisher(pstmt.executeReactive(t1))
  }

  implicit class PagerOps[Out](private val pager: Pager[Out]) extends AnyVal {

    /** An [[Observable]] reading from Cassandra
      *
      * @param pageSize how many rows to fetch
      */
    def asObservable(pageSize: Int)(
        implicit session: CqlSession
    ): Observable[(Pager[Out], Out)] =
      Observable.fromReactivePublisher(pager.executeReactive(pageSize))

  }

  implicit class ScalaPreparedStatementOps[In, Out](
      private val pstmt: ScalaPreparedStatement[In, Out]
  ) extends AnyVal {

    /** Creates a [[Consumer]] that will consume data from an [[Observable]]
      *
      * This is meant to be used for writing or updating to Cassandra
      */
    def asConsumer()(implicit session: CqlSession): Consumer[In, Unit] =
      Consumer.create[In, Unit] { (_, _, callback) =>
        new Observer.Sync[In] {

          override def onNext(elem: In): Ack = {
            session.execute(pstmt.tupled(elem))
            Ack.Continue
          }

          override def onError(ex: Throwable): Unit = callback.onError(ex)

          override def onComplete(): Unit = callback.onSuccess(())
        }
      }
  }

  implicit class CassandraObservableOps[In](private val obs: Observable[In]) extends AnyVal {

    def batchedConsumer[K](
        pstmt: ScalaPreparedStatement[In, _],
        groupingKey: In => K,
        maxBatchSize: Int,
        maxBatchWait: FiniteDuration,
        batchType: BatchType
    )(implicit session: CqlSession): Task[Unit] =
      obs
        .bufferTimedAndCounted(maxBatchWait, maxBatchSize)
        .map(value => Observable.pure(value.groupBy(groupingKey).values.flatten))
        .flatten
        .consumeWith(
          Consumer.create[Iterable[In], Unit] { (_, _, callback) =>
            import scala.jdk.CollectionConverters._

            new Observer.Sync[Iterable[In]] {

              override def onNext(elem: Iterable[In]): Ack = {
                val batch          = elem.map(pstmt.tupled)
                val batchStatement = BatchStatement.newInstance(batchType).addAll(batch.asJava)
                session.execute(batchStatement)

                Ack.Continue
              }

              override def onError(ex: Throwable): Unit = callback.onError(ex)

              override def onComplete(): Unit = callback.onSuccess(())
            }
          }
        )

  }


  // format: off
  // $COVERAGE-OFF$
  implicit class ScalaPreparedStatement2Ops[T1, T2, T3, Out](private val pstmt: ScalaPreparedStatement2[T1, T2, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2))
    }
  }

  implicit class ScalaPreparedStatement3Ops[T1, T2, T3, Out](private val pstmt: ScalaPreparedStatement3[T1, T2, T3, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3))
    }
  }

  implicit class ScalaPreparedStatement4Ops[T1, T2, T3, T4, Out](private val pstmt: ScalaPreparedStatement4[T1, T2, T3, T4, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4))
    }
  }

  implicit class ScalaPreparedStatement5Ops[T1, T2, T3, T4, T5, Out](private val pstmt: ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5))
    }
  }

  implicit class ScalaPreparedStatement6Ops[T1, T2, T3, T4, T5, T6, Out](private val pstmt: ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6))
    }
  }

  implicit class ScalaPreparedStatement7Ops[T1, T2, T3, T4, T5, T6, T7, Out](private val pstmt: ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7))
    }
  }

  implicit class ScalaPreparedStatement8Ops[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val pstmt: ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8))
    }
  }

  implicit class ScalaPreparedStatement9Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val pstmt: ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9))
    }
  }

  implicit class ScalaPreparedStatement10Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val pstmt: ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))
    }
  }

  implicit class ScalaPreparedStatement11Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val pstmt: ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))
    }
  }

  implicit class ScalaPreparedStatement12Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val pstmt: ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))
    }
  }

  implicit class ScalaPreparedStatement13Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val pstmt: ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))
    }
  }

  implicit class ScalaPreparedStatement14Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val pstmt: ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))
    }
  }

  implicit class ScalaPreparedStatement15Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val pstmt: ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))
    }
  }

  implicit class ScalaPreparedStatement16Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val pstmt: ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))
    }
  }

  implicit class ScalaPreparedStatement17Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val pstmt: ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))
    }
  }

  implicit class ScalaPreparedStatement18Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val pstmt: ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))
    }
  }

  implicit class ScalaPreparedStatement19Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val pstmt: ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))
    }
  }

  implicit class ScalaPreparedStatement20Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val pstmt: ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))
    }
  }

  implicit class ScalaPreparedStatement21Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val pstmt: ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))
    }
  }

  implicit class ScalaPreparedStatement22Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val pstmt: ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromReactivePublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))
    }
  }
  // $COVERAGE-ON$
  // format: on

  implicit class WrappedBoundStatementAsyncOps[Out](
      private val wbs: Future[WrappedBoundStatement[Out]]
  ) extends AnyVal {

    /** An [[Observable]] reading from Cassandra */
    def asObservable()(implicit session: CqlSession): Observable[Out] =
      Observable
        .fromFuture(wbs)
        .flatMap(w => Observable.fromReactivePublisher(w.executeReactive()))

  }

  implicit class ScalaPreparedStatementUnitAsyncOps[Out](
      private val pstmt: Future[ScalaPreparedStatementUnit[Out]]
  ) extends AnyVal {

    /** An [[Observable]] reading from Cassandra */
    def asObservable()(
        implicit session: CqlSession
    ): Observable[Out] =
      Observable
        .fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive()))

  }

  implicit class ScalaPreparedStatement1AsyncOps[T1, Out](
      private val pstmt: Future[ScalaPreparedStatement1[T1, Out]]
  ) extends AnyVal {
    def asObservable(t1: T1)(implicit session: CqlSession): Observable[Out] =
      Observable
        .fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1)))
  }

  implicit class PagerAsyncOps[Out](private val pager: Future[Pager[Out]]) extends AnyVal {

    /** An [[Observable]] reading from Cassandra
      *
      * @param pageSize how many rows to fetch
      */
    def asObservable(pageSize: Int)(
        implicit session: CqlSession
    ): Observable[(Pager[Out], Out)] =
      Observable
        .fromFuture(pager)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(pageSize)))

  }

  implicit class ScalaPreparedStatementAsyncOps[In, Out](
      private val futPstmt: Future[ScalaPreparedStatement[In, Out]]
  ) extends AnyVal {

    /** Creates a [[Consumer]] that will consume data from an [[Observable]]
      *
      * This is meant to be used for writing or updating to Cassandra
      */
    def asConsumer()(implicit session: CqlSession): Consumer[In, Unit] =
      Consumer.create[In, Unit] { (scheduler, _, callback) =>
        new Observer[In] {
          private implicit val ec: ExecutionContext = scheduler

          override def onNext(elem: In): Future[Ack] =
            for {
              pstmt <- futPstmt
              _ <- session.executeAsync(pstmt.tupled(elem)).asScala
            } yield Ack.Continue

          override def onError(ex: Throwable): Unit = callback.onError(ex)

          override def onComplete(): Unit = callback.onSuccess(())
        }
      }
  }
  
  // format: off
  // $COVERAGE-OFF$
  implicit class ScalaPreparedStatement2AsyncOps[T1, T2, T3, Out](private val pstmt: Future[ScalaPreparedStatement2[T1, T2, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2)))
    }
  }

  implicit class ScalaPreparedStatement3AsyncOps[T1, T2, T3, Out](private val pstmt: Future[ScalaPreparedStatement3[T1, T2, T3, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3)))
    }
  }

  implicit class ScalaPreparedStatement4AsyncOps[T1, T2, T3, T4, Out](private val pstmt: Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4)))
    }
  }

  implicit class ScalaPreparedStatement5AsyncOps[T1, T2, T3, T4, T5, Out](private val pstmt: Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5)))
    }
  }

  implicit class ScalaPreparedStatement6AsyncOps[T1, T2, T3, T4, T5, T6, Out](private val pstmt: Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6)))
    }
  }

  implicit class ScalaPreparedStatement7AsyncOps[T1, T2, T3, T4, T5, T6, T7, Out](private val pstmt: Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7)))
    }
  }

  implicit class ScalaPreparedStatement8AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val pstmt: Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8)))
    }
  }

  implicit class ScalaPreparedStatement9AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val pstmt: Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9)))
    }
  }

  implicit class ScalaPreparedStatement10AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val pstmt: Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)))
    }
  }

  implicit class ScalaPreparedStatement11AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val pstmt: Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11)))
    }
  }

  implicit class ScalaPreparedStatement12AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val pstmt: Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12)))
    }
  }

  implicit class ScalaPreparedStatement13AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val pstmt: Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13)))
    }
  }

  implicit class ScalaPreparedStatement14AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val pstmt: Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14)))
    }
  }

  implicit class ScalaPreparedStatement15AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val pstmt: Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15)))
    }
  }

  implicit class ScalaPreparedStatement16AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val pstmt: Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16)))
    }
  }

  implicit class ScalaPreparedStatement17AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val pstmt: Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17)))
    }
  }

  implicit class ScalaPreparedStatement18AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val pstmt: Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18)))
    }
  }

  implicit class ScalaPreparedStatement19AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val pstmt: Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19)))
    }
  }

  implicit class ScalaPreparedStatement20AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val pstmt: Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20)))
    }
  }

  implicit class ScalaPreparedStatement21AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val pstmt: Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21)))
    }
  }

  implicit class ScalaPreparedStatement22AsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val pstmt: Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]]) extends AnyVal {
    /** An [[Observable]] reading from Cassandra */
    def asObservable(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): Observable[Out] = {
      Observable.fromFuture(pstmt)
        .flatMap(p => Observable.fromReactivePublisher(p.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22)))
    }
  }
  // $COVERAGE-ON$
  // format: on

}
