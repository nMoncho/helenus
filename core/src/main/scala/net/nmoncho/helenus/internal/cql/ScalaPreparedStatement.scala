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

package net.nmoncho.helenus.internal.cql

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.{ CqlSession, PagingIterable }
import net.nmoncho.helenus.api.`type`.codec.RowMapper
import net.nmoncho.helenus.internal.CqlSessionSyncExtension

import java.nio.ByteBuffer
import java.util
import scala.concurrent.{ ExecutionContext, Future }

/** Wraps a [[PreparedStatement]] while providing an `apply` method to produce
  * a [[BoundStatement]]
  */
class ScalaPreparedStatement[U, T](
    fn: U => BoundStatement,
    mapper: RowMapper[T],
    pstmt: PreparedStatement
) extends PreparedStatement {

  import net.nmoncho.helenus._

  /** Returns a [[BoundStatement]] for parameters provided.
    *
    * This method is intended mainly for integration with some other libraries, like Akka Streams.
    */
  def apply(u: U): BoundStatement = fn(u)

  /** Executes [[PreparedStatement]] with the provided [[U]] parameters, returning a [[PagingIterable]] of [[T]]
    */
  def execute(u: U)(implicit session: CqlSessionSyncExtension): PagingIterable[T] =
    apply(u).execute().as[T](mapper)

  /** Converts a [[ScalaPreparedStatement]] to map [[Row]] results to [[A]]
    */
  def as[A](implicit mapper: RowMapper[A], ev: T =:= Row): ScalaPreparedStatement[U, A] =
    new ScalaPreparedStatement(fn, mapper, pstmt)

  override def getId: ByteBuffer = pstmt.getId

  override def getQuery: String = pstmt.getQuery

  override def getVariableDefinitions: ColumnDefinitions = pstmt.getVariableDefinitions

  override def getPartitionKeyIndices: util.List[Integer] = pstmt.getPartitionKeyIndices

  override def getResultMetadataId: ByteBuffer = pstmt.getResultMetadataId

  override def getResultSetDefinitions: ColumnDefinitions = pstmt.getResultSetDefinitions

  override def setResultMetadata(
      newResultMetadataId: ByteBuffer,
      newResultSetDefinitions: ColumnDefinitions
  ): Unit =
    pstmt.setResultMetadata(newResultMetadataId, newResultSetDefinitions)

  override def bind(values: AnyRef*): BoundStatement =
    pstmt.bind(values: _*)

  override def boundStatementBuilder(values: AnyRef*): BoundStatementBuilder =
    pstmt.boundStatementBuilder(values: _*)

}

object ScalaPreparedStatement {

  case class CQLQuery(query: String, session: CqlSession) extends SyncCQLQuery with AsyncCQLQuery

  // $COVERAGE-OFF$
  trait SyncCQLQuery {

    def query: String
    def session: CqlSession

    // **********************************************************************
    // To generate methods to Tuple2 and above, use this template method.
    // **********************************************************************
    //
    //  def template(t: Int): String = {
    //    val ts             = (1 to t).map(i => s"t$i")
    //    val Ts             = ts.map(_.toUpperCase)
    //    val typeParameters = Ts.mkString(", ")
    //    val codecs = ts
    //      .zip(Ts)
    //      .map { case (param, typ) =>
    //        s"${param}: TypeCodec[$typ]"
    //      }
    //      .mkString(", ")
    //    val setters = (1 to t).map(i => s".set(${i - 1}, t._$i, t$i)").mkString("")
    //
    //    s"""def prepare[$typeParameters](implicit $codecs): ScalaPreparedStatement[($typeParameters), Row] = {
    //       |    val pstmt = session.prepare(query)
    //       |
    //       |    new ScalaPreparedStatement[($typeParameters), Row](
    //       |      (t: ($typeParameters)) => pstmt.bind()${setters},
    //       |      RowMapper.identity,
    //       |      pstmt
    //       |    )
    //       |  }
    //       |""".stripMargin
    //  }
    //
    // (2 to 22).map(template).foreach(println(_))

    // format: off

    /**
     * Prepares a query that will take 1 query parameter, which can be invoked like:
     * {{{
     *   import net.nmoncho.helenus.api._
     *
     *   val pstmt = "SELECT * FROM users WHERE id = ?".toCQL.prepare[String]
     *   val bstmt = pstmt("bob")
     * }}}
     *
     * @return BoundStatement that can be called like a function
     */
    def prepare[T1](implicit t1: TypeCodec[T1]): ScalaPreparedStatement[T1, Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[T1, Row](
        (t: T1) => pstmt.bind().set(0, t, t1),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2](implicit t1: TypeCodec[T1], t2: TypeCodec[T2]): ScalaPreparedStatement[(T1, T2), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2), Row](
        (t: (T1, T2)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3]): ScalaPreparedStatement[(T1, T2, T3), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3), Row](
        (t: (T1, T2, T3)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4]): ScalaPreparedStatement[(T1, T2, T3, T4), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4), Row](
        (t: (T1, T2, T3, T4)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5]): ScalaPreparedStatement[(T1, T2, T3, T4, T5), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5), Row](
        (t: (T1, T2, T3, T4, T5)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Row](
        (t: (T1, T2, T3, T4, T5, T6)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20).set(20, t._21, t21),
        RowMapper.identity,
        pstmt
      )
    }

    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21], t22: TypeCodec[T22]): ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Row] = {
      val pstmt = session.prepare(query)

      new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Row](
        (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20).set(20, t._21, t21).set(21, t._22, t22),
        RowMapper.identity,
        pstmt
      )
    }
    // format: on
  }

  trait AsyncCQLQuery {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    def query: String
    def session: CqlSession

    // format: off

    // **********************************************************************
    // To generate methods to Tuple2 and above, use this template method.
    // **********************************************************************
    //
    //  def template(t: Int): String = {
    //    val ts             = (1 to t).map(i => s"t$i")
    //    val Ts             = ts.map(_.toUpperCase)
    //    val typeParameters = Ts.mkString(", ")
    //    val codecs = ts
    //      .zip(Ts)
    //      .map { case (param, typ) =>
    //        s"${param}: TypeCodec[$typ]"
    //      }
    //      .mkString(", ")
    //    val setters = (1 to t).map(i => s".set(${i - 1}, t._$i, t$i)").mkString("")
    //
    //    s"""def prepareAsync[$typeParameters](implicit ec: ExecutionContext, $codecs): Future[ScalaPreparedStatement[($typeParameters), Row]] =
    //       |  session.prepareAsync(query).asScala.map { pstmt =>
    //       |    new ScalaPreparedStatement[($typeParameters), Row](
    //       |      (t: ($typeParameters)) => pstmt.bind()${setters},
    //       |      RowMapper.identity,
    //       |      pstmt
    //       |    )
    //       |  }
    //       |""".stripMargin
    //  }
    //
    // (2 to 22).map(template).foreach(println(_))

    def prepareAsync[T1](implicit ec: ExecutionContext, t1: TypeCodec[T1]): Future[ScalaPreparedStatement[T1, Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[T1, Row](
          (t: T1) => pstmt.bind().set(0, t, t1),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2]): Future[ScalaPreparedStatement[(T1, T2), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2), Row](
          (t: (T1, T2)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3]): Future[ScalaPreparedStatement[(T1, T2, T3), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3), Row](
          (t: (T1, T2, T3)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4]): Future[ScalaPreparedStatement[(T1, T2, T3, T4), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4), Row](
          (t: (T1, T2, T3, T4)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5), Row](
          (t: (T1, T2, T3, T4, T5)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Row](
          (t: (T1, T2, T3, T4, T5, T6)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20).set(20, t._21, t21),
          RowMapper.identity,
          pstmt
        )
      }

    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](implicit ec: ExecutionContext, t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21], t22: TypeCodec[T22]): Future[ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Row]] =
      session.prepareAsync(query).asScala.map { pstmt =>
        new ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Row](
          (t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)) => pstmt.bind().set(0, t._1, t1).set(1, t._2, t2).set(2, t._3, t3).set(3, t._4, t4).set(4, t._5, t5).set(5, t._6, t6).set(6, t._7, t7).set(7, t._8, t8).set(8, t._9, t9).set(9, t._10, t10).set(10, t._11, t11).set(11, t._12, t12).set(12, t._13, t13).set(13, t._14, t14).set(14, t._15, t15).set(15, t._16, t16).set(16, t._17, t17).set(17, t._18, t18).set(18, t._19, t19).set(19, t._20, t20).set(20, t._21, t21).set(21, t._22, t22),
          RowMapper.identity,
          pstmt
        )
      }

    // format: on
  }
  // $COVERAGE-ON$
}
