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

package net.nmoncho.helenus

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import _root_.akka.Done
import _root_.akka.NotUsed
import _root_.akka.stream.alpakka.cassandra.CassandraWriteSettings
import _root_.akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import _root_.akka.stream.scaladsl._
import _root_.net.nmoncho.helenus.internal.cql._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BatchStatement

package object akka {

  implicit def toExtension(implicit session: CassandraSession): Future[CqlSession] =
    session.underlying()

  implicit class ScalaPreparedStatementUnitAkkaReadSyncOps[Out](
      private val pstmt: ScalaPreparedStatementUnit[Out]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      */
    def asReadSource()(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source
        .future(session.underlying())
        .flatMapConcat { implicit cqlSession =>
          Source.fromPublisher(pstmt.executeReactive())
        }

  }

  implicit class ScalaPreparedStatementUnitAkkaReadAsyncOps[Out](
      private val pstmt: Future[ScalaPreparedStatementUnit[Out]]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      */
    def asReadSource()(
        implicit session: CassandraSession,
        ec: ExecutionContext
    ): Source[Out, NotUsed] = {
      Source.futureSource(
        pstmt.map(_.asReadSource())
      )
    }.mapMaterializedValue(_ => NotUsed)

  }

  implicit class ScalaPreparedStatementAkkaReadSyncOps[In, Out](
      private val pstmt: ScalaPreparedStatement1[In, Out]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      *
      * @param u query parameters
      */
    def asReadSource(u: In)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source
        .future(session.underlying())
        .flatMapConcat { implicit cqlSession =>
          Source.fromPublisher(pstmt.executeReactive(u))
        }
  }

  implicit class ScalaPreparedStatementAkkaReadAsyncOps[In, Out](
      private val pstmt: Future[ScalaPreparedStatement1[In, Out]]
  ) extends AnyVal {

    /** A `Source` reading from Cassandra
      *
      * @param u query parameters
      */
    def asReadSource(u: In)(
        implicit session: CassandraSession,
        ec: ExecutionContext
    ): Source[Out, NotUsed] =
      Source
        .futureSource(
          pstmt.map(
            _.asReadSource(u)
          )
        )
        .mapMaterializedValue(_ => NotUsed)

  }

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  //
  // def template(typeParameterCount: Int): Unit = {
  //    val typeParameters = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //    val parameterList = (1 to typeParameterCount).map(i => s"t$i: T$i").mkString(", ")
  //    val methodParameters = (1 to typeParameterCount).map(i => s"t$i").mkString(", ")
  //
  //    val opsTemplate = s"""
  //        |implicit class ScalaPreparedStatement${typeParameterCount}AkkaReadSyncOps[$typeParameters, Out](private val pstmt: ScalaPreparedStatement${typeParameterCount}[$typeParameters, Out]) extends AnyVal {
  //        |  def asReadSource($parameterList)(implicit session: CassandraSession): Source[Out, NotUsed] =
  //        |    Source.future(session.underlying()).flatMapConcat { implicit cqlSession => Source.fromPublisher(pstmt.executeReactive($methodParameters)) }
  //        |}
  //        |
  //        |implicit class ScalaPreparedStatement${typeParameterCount}AkkaReadAsyncOps[$typeParameters, Out](private val pstmt: Future[ScalaPreparedStatement${typeParameterCount}[$typeParameters, Out]]) extends AnyVal {
  //        |  def asReadSource($parameterList)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
  //        |    Source.futureSource(pstmt.map(_.asReadSource($methodParameters))).mapMaterializedValue(_ => NotUsed)
  //        |}
  //        |""".stripMargin
  //
  //    println(opsTemplate)
  // }
  //
  // (2 to 22).foreach(template)

  // format: off
  // $COVERAGE-OFF$
  implicit class ScalaPreparedStatement2AkkaReadSyncOps[T1, T2, Out](private val pstmt: ScalaPreparedStatement2[T1, T2, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2))}
  }

  implicit class ScalaPreparedStatement2AkkaReadAsyncOps[T1, T2, Out](private val pstmt: Future[ScalaPreparedStatement2[T1, T2, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement3AkkaReadSyncOps[T1, T2, T3, Out](private val pstmt: ScalaPreparedStatement3[T1, T2, T3, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3))}
  }

  implicit class ScalaPreparedStatement3AkkaReadAsyncOps[T1, T2, T3, Out](private val pstmt: Future[ScalaPreparedStatement3[T1, T2, T3, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement4AkkaReadSyncOps[T1, T2, T3, T4, Out](private val pstmt: ScalaPreparedStatement4[T1, T2, T3, T4, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4))}
  }

  implicit class ScalaPreparedStatement4AkkaReadAsyncOps[T1, T2, T3, T4, Out](private val pstmt: Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement5AkkaReadSyncOps[T1, T2, T3, T4, T5, Out](private val pstmt: ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5))}
  }

  implicit class ScalaPreparedStatement5AkkaReadAsyncOps[T1, T2, T3, T4, T5, Out](private val pstmt: Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement6AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, Out](private val pstmt: ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6))}
  }

  implicit class ScalaPreparedStatement6AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, Out](private val pstmt: Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement7AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, Out](private val pstmt: ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7))}
  }

  implicit class ScalaPreparedStatement7AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, Out](private val pstmt: Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement8AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val pstmt: ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8))}
  }

  implicit class ScalaPreparedStatement8AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val pstmt: Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement9AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val pstmt: ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9))}
  }

  implicit class ScalaPreparedStatement9AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val pstmt: Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement10AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val pstmt: ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))}
  }

  implicit class ScalaPreparedStatement10AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val pstmt: Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement11AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val pstmt: ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))}
  }

  implicit class ScalaPreparedStatement11AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val pstmt: Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement12AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val pstmt: ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))}
  }

  implicit class ScalaPreparedStatement12AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val pstmt: Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement13AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val pstmt: ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))}
  }

  implicit class ScalaPreparedStatement13AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val pstmt: Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement14AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val pstmt: ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))}
  }

  implicit class ScalaPreparedStatement14AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val pstmt: Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement15AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val pstmt: ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))}
  }

  implicit class ScalaPreparedStatement15AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val pstmt: Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement16AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val pstmt: ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))}
  }

  implicit class ScalaPreparedStatement16AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val pstmt: Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement17AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val pstmt: ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))}
  }

  implicit class ScalaPreparedStatement17AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val pstmt: Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement18AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val pstmt: ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))}
  }

  implicit class ScalaPreparedStatement18AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val pstmt: Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement19AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val pstmt: ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))}
  }

  implicit class ScalaPreparedStatement19AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val pstmt: Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement20AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val pstmt: ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))}
  }

  implicit class ScalaPreparedStatement20AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val pstmt: Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement21AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val pstmt: ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))}
  }

  implicit class ScalaPreparedStatement21AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val pstmt: Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))).mapMaterializedValue(_ => NotUsed)
  }

  implicit class ScalaPreparedStatement22AkkaReadSyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val pstmt: ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CassandraSession): Source[Out, NotUsed] =
      Source.future(session.underlying()).flatMapConcat {implicit cqlSession => Source.fromPublisher(pstmt.executeReactive(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))}
  }

  implicit class ScalaPreparedStatement22AkkaReadAsyncOps[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val pstmt: Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]]) extends AnyVal {
    def asReadSource(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CassandraSession, ec: ExecutionContext): Source[Out, NotUsed] =
      Source.futureSource(pstmt.map(_.asReadSource(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))).mapMaterializedValue(_ => NotUsed)
  }
  // $COVERAGE-ON$
  // format: on

  implicit class ScalaPreparedStatementAkkaWriteOps[U, T](
      private val pstmt: ScalaPreparedStatement[U, T]
  ) extends AnyVal {

    /** A `Flow` writing to Cassandra for every stream element.
      * The element to be persisted is emitted unchanged.
      *
      * @param writeSettings   settings to configure the write operation
      * @param session         implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteFlow(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession): Flow[U, U, NotUsed] =
      Flow
        .lazyFlow { () =>
          Flow[U]
            .mapAsync(writeSettings.parallelism) { element =>
              session
                .executeWrite(pstmt.tupled(element))
                .map(_ => element)(ExecutionContext.parasitic)
            }
        }
        .mapMaterializedValue(_ => NotUsed)

    def asWriteFlowWithContext[Ctx](
        writeSettings: CassandraWriteSettings
    )(
        implicit session: CassandraSession
    ): FlowWithContext[U, Ctx, U, Ctx, NotUsed] =
      FlowWithContext.fromTuples {
        Flow
          .lazyFlow { () =>
            Flow[(U, Ctx)].mapAsync(writeSettings.parallelism) { case tuple @ (element, _) =>
              session
                .executeWrite(pstmt.tupled(element))
                .map(_ => tuple)(ExecutionContext.parasitic)
            }
          }
          .mapMaterializedValue(_ => NotUsed)
      }

    /** Creates a `Flow` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings   settings to configure the batching and the write operation
      * @param groupingKey     groups the elements to go into the same batch
      * @param session         implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteFlowBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession): Flow[U, U, NotUsed] = {
      import scala.jdk.CollectionConverters._

      Flow
        .lazyFlow { () =>
          Flow[U]
            .groupedWithin(writeSettings.maxBatchSize, writeSettings.maxBatchWait)
            .map(_.groupBy(groupingKey).values.toList)
            .mapConcat(identity)
            .mapAsyncUnordered(writeSettings.parallelism) { list =>
              val boundStatements = list.map(pstmt.tupled)
              val batchStatement =
                BatchStatement.newInstance(writeSettings.batchType).addAll(boundStatements.asJava)
              session.executeWriteBatch(batchStatement).map(_ => list)(ExecutionContext.parasitic)
            }
            .mapConcat(_.toList)
        }
        .mapMaterializedValue(_ => NotUsed)
    }

    /** A `Sink` writing to Cassandra for every stream element.
      *
      * Unlike [[asWriteFlow]], stream elements are ignored after being persisted.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteSink(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession): Sink[U, Future[Done]] =
      asWriteFlow(writeSettings)
        .toMat(Sink.ignore)(Keep.right)

    /** Creates a `Sink` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteSinkBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession): Sink[U, Future[Done]] =
      asWriteFlowBatched(writeSettings, groupingKey)
        .toMat(Sink.ignore)(Keep.right)
  }

  implicit class AsyncScalaPreparedStatementAkkaWriteOps[U, T](
      private val futurePstmt: Future[ScalaPreparedStatement[U, T]]
  ) extends AnyVal {

    /** A `Flow` writing to Cassandra for every stream element.
      * The element to be persisted is emitted unchanged.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteFlow(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession, ec: ExecutionContext): Flow[U, U, NotUsed] =
      Flow
        .lazyFlow { () =>
          Flow[U]
            .mapAsync(writeSettings.parallelism) { element =>
              for {
                pstmt <- futurePstmt
                _ <- session.executeWrite(pstmt.tupled(element))
              } yield element
            }
        }
        .mapMaterializedValue(_ => NotUsed)

    def asWriteFlowWithContext[Ctx](
        writeSettings: CassandraWriteSettings
    )(
        implicit session: CassandraSession,
        ec: ExecutionContext
    ): FlowWithContext[U, Ctx, U, Ctx, NotUsed] =
      FlowWithContext.fromTuples {
        Flow
          .lazyFlow { () =>
            Flow[(U, Ctx)].mapAsync(writeSettings.parallelism) { case tuple @ (element, _) =>
              for {
                pstmt <- futurePstmt
                _ <- session.executeWrite(pstmt.tupled(element))
              } yield tuple
            }
          }
          .mapMaterializedValue(_ => NotUsed)
      }

    /** Creates a `Flow` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteFlowBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession, ec: ExecutionContext): Flow[U, U, NotUsed] = {
      import scala.jdk.CollectionConverters._

      Flow
        .lazyFlow { () =>
          Flow[U]
            .groupedWithin(writeSettings.maxBatchSize, writeSettings.maxBatchWait)
            .map(_.groupBy(groupingKey).values.toList)
            .mapConcat(identity)
            .mapAsyncUnordered(writeSettings.parallelism) { list =>
              for {
                boundStatements <- Future.traverse(list)(element =>
                  futurePstmt.map(_.tupled(element))
                )
                batchStatement =
                  BatchStatement.newInstance(writeSettings.batchType).addAll(boundStatements.asJava)
                execution <- session.executeWriteBatch(batchStatement).map(_ => list)(ec)
              } yield execution
            }
            .mapConcat(_.toList)
        }
        .mapMaterializedValue(_ => NotUsed)
    }

    /** A `Sink` writing to Cassandra for every stream element.
      *
      * Unlike [[asWriteFlow]], stream elements are ignored after being persisted.
      *
      * @param writeSettings settings to configure the write operation
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      */
    def asWriteSink(
        writeSettings: CassandraWriteSettings
    )(implicit session: CassandraSession, ec: ExecutionContext): Sink[U, Future[Done]] =
      asWriteFlow(writeSettings)
        .toMat(Sink.ignore)(Keep.right)

    /** Creates a `Sink` that uses [[com.datastax.oss.driver.api.core.cql.BatchStatement]] and groups the
      * elements internally into batches using the `writeSettings` and per `groupingKey`.
      * Use this when most of the elements in the stream share the same partition key.
      *
      * Cassandra batches that share the same partition key will only
      * resolve to one write internally in Cassandra, boosting write performance.
      *
      * "A LOGGED batch to a single partition will be converted to an UNLOGGED batch as an optimization."
      * ([[https://cassandra.apache.org/doc/latest/cql/dml.html#batch Batch CQL]])
      *
      * Be aware that this stage does NOT preserve the upstream order.
      *
      * @param writeSettings settings to configure the batching and the write operation
      * @param groupingKey   groups the elements to go into the same batch
      * @param session       implicit Cassandra session from `CassandraSessionRegistry`
      * @tparam K extracted key type for grouping into batches
      */
    def asWriteSinkBatched[K](
        writeSettings: CassandraWriteSettings,
        groupingKey: U => K
    )(implicit session: CassandraSession, ec: ExecutionContext): Sink[U, Future[Done]] =
      asWriteFlowBatched(writeSettings, groupingKey)
        .toMat(Sink.ignore)(Keep.right)
  }

}
