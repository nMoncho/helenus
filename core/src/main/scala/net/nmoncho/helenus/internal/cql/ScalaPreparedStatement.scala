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

import java.nio.ByteBuffer
import java.util

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement.BoundStatementOps
import org.reactivestreams.Publisher

// format: off

/** A `ScalaPreparedStatement` extends and wraps a [[PreparedStatement]], delegating all methods to the contained instance
 *
 * This class serves as the basic abstraction for <em>all</em> statements.
 *
 * @param pstmt wrapped instance
 * @param mapper how to map results into [[Out]] values
 * @tparam In statement input value
 * @tparam Out statement output value
 */
abstract class ScalaPreparedStatement[In, Out](pstmt: PreparedStatement, mapper: RowMapper[Out]) extends PreparedStatement {

  type AsOut[T] <: ScalaPreparedStatement[_, T]

  // Since this is no longer exposed to users, we can use the tupled `apply` function
  def tupled: In => BoundStatement

  /** Adapts this [[ScalaPreparedStatement]] converting [[In2]] values with the provided adapter
   * into a [[In]] value (ie. the original type of this statement)
   *
   * @param adapter how to adapt an [[In2]] value into [[In]] value
   * @tparam In2 new input type
   * @return adapted [[ScalaPreparedStatement]] with new [[In2]] input type
   */
  def from[In2](implicit adapter: Adapter[In2, In]): AdaptedScalaPreparedStatement[In2, In, Out] =
    new AdaptedScalaPreparedStatement[In2, In, Out](this, mapper, adapter)

  /** Maps the result from this [[PreparedStatement]] with a different [[Out2]]
   * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
   * meant to avoid calling `as` twice)
   */
  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2]

  // ----------------------------------------------------------------------------
  //  Wrapped `PreparedStatement` methods
  // ----------------------------------------------------------------------------

  override def getId: ByteBuffer = pstmt.getId

  override def getQuery: String = pstmt.getQuery

  override def getVariableDefinitions: ColumnDefinitions = pstmt.getVariableDefinitions

  override def getPartitionKeyIndices: util.List[Integer] = pstmt.getPartitionKeyIndices

  override def getResultMetadataId: ByteBuffer = pstmt.getResultMetadataId

  override def getResultSetDefinitions: ColumnDefinitions = pstmt.getResultSetDefinitions

  override def setResultMetadata(id: ByteBuffer, definitions: ColumnDefinitions): Unit =
    pstmt.setResultMetadata(id, definitions)

  override def bind(values: AnyRef*): BoundStatement =
    pstmt.bind(values: _*)

  override def boundStatementBuilder(values: AnyRef*): BoundStatementBuilder =
    pstmt.boundStatementBuilder(values: _*)
}

object ScalaPreparedStatement {

  implicit private[cql] class BoundStatementOps(private val bs: BoundStatement) extends AnyVal {

    /** Sets or binds the specified value only if it's not NULL, avoiding a tombstone insert.
     *
     * @param index position of bound parameter
     * @param value value to be bound
     * @param codec how to encode the provided value
     * @tparam T
     * @return a modified version of this [[BoundStatement]]
     */
    def setIfDefined[T](index: Int, value: T, codec: TypeCodec[T]): BoundStatement = {
      if (value == null || value == None) bs else bs.set(index, value, codec)
    }
  }

}

/** Adapts the input for a [[ScalaPreparedStatement]] from [[In2]] to [[In]] through an [[Adapter]].
 *
 * This useful when inserting `case classes` into tables
 *
 * @param pstmt wrapped instance
 * @param mapper how to map results into [[Out]] values
 * @param adapter how to adapt [[In2]] to [[In]]
 * @tparam In2 new input value
 * @tparam In statement input value
 * @tparam Out statement output value
 */
class AdaptedScalaPreparedStatement[In2, In, Out](pstmt: ScalaPreparedStatement[In, _], mapper: RowMapper[Out], adapter: Adapter[In2, In])
    extends ScalaPreparedStatement[In2, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = AdaptedScalaPreparedStatement[In2, In, T]

  override val tupled: In2 => BoundStatement = apply

  /** Bounds an input [[In]] value and returns a [[BoundStatement]] */
  def apply(t1: In2): BoundStatement = pstmt.tupled(adapter(t1))

  /** Executes this [[PreparedStatement]] with the provided value.
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: In2)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion using
   * the provided [[In]] input value
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: In2)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: In2)(implicit session: CqlSession): Publisher[Out] =
    apply(t1).executeReactive().as[Out](mapper)

  override def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2] =
    new AdaptedScalaPreparedStatement[In2, In, Out2](pstmt, mapper, adapter)
}

/** A [[PreparedStatement]] without input parameters
 *
 * @param pstmt wrapped instance
 * @param mapper maps [[Row]]s into [[Out]] values
 * @tparam Out output value
 */
class ScalaPreparedStatementUnit[Out](pstmt: PreparedStatement, mapper: RowMapper[Out])
    extends ScalaPreparedStatement[Unit, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatementUnit[T]

  override val tupled: Unit => BoundStatement = _ => apply

  /** Returns a [[BoundStatement]] */
  def apply(): BoundStatement = pstmt.bind()

  /** Executes this [[PreparedStatement]]
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute()(implicit session: CqlSession): PagingIterable[Out] =
    apply().execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync()(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply().executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive()(implicit session: CqlSession): Publisher[Out] =
    apply().executeReactive().as[Out](mapper)

  /** Maps the result from this [[PreparedStatement]] with a different [[Out2]]
   * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
   * meant to avoid calling `as` twice)
   */
  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2] =
    new ScalaPreparedStatementUnit[Out2](pstmt, mapper)
}

/** A [[PreparedStatement]] with one input parameter
 *
 * @param pstmt wrapped instance
 * @param mapper maps [[Row]]s into [[Out]] values
 * @param t1Codec how to encode [[T1]] values
 * @tparam T1 input value
 * @tparam Out output value
 */
class ScalaPreparedStatement1[T1, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1])
    extends ScalaPreparedStatement[T1, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement1[T1, T]

  override def tupled: T1 => BoundStatement = apply

  /** Bounds an input [[T1]] value and returns a [[BoundStatement]] */
  def apply(t1: T1): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec)

  /** Executes this [[PreparedStatement]] with the provided value.
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion using
   * the provided [[T1]] input value
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1)(implicit session: CqlSession): Publisher[Out] =
    apply(t1).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2] =
    new ScalaPreparedStatement1(pstmt, mapper, t1Codec)
}

// **********************************************************************
// To generate methods to Tuple2 and above, use this template method.
// **********************************************************************
//
// def template(typeParameterCount: Int): Unit = {
//    val typeParameters = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
//    val typeCodecs = (1 to typeParameterCount).map(i => s"t${i}Codec: TypeCodec[T$i]").mkString(", ")
//    val codecParams = (1 to typeParameterCount).map(i => s"t${i}Codec").mkString(", ")
//    val parameterList = (1 to typeParameterCount).map(i => s"t$i: T$i").mkString(", ")
//    val parameterBindings = (0 until typeParameterCount).map(i => s".setIfDefined($i, t${i + 1}, t${i + 1}Codec)").mkString("")
//    val methodParameters = (1 to typeParameterCount).map(i => s"t$i").mkString(", ")
//
//    val classTemplate = s"""
//        |class ScalaPreparedStatement$typeParameterCount[$typeParameters, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], $typeCodecs)
//        |    extends ScalaPreparedStatement[($typeParameters), Out](pstmt, mapper) {
//        |
//        |  import net.nmoncho.helenus._
//        |
//        |  override type AsOut[T] = ScalaPreparedStatement$typeParameterCount[$typeParameters, T]
//        |
//        |  override val tupled: (($typeParameters)) => BoundStatement = (apply _).tupled
//        |
//        |  def apply($parameterList): BoundStatement =
//        |    pstmt.bind()$parameterBindings
//        |
//        |  def execute($parameterList)(implicit session: CqlSession): PagingIterable[Out] =
//        |    apply($methodParameters).execute().as[Out](mapper)
//        |
//        |  def executeAsync($parameterList)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
//        |    apply($methodParameters).executeAsync().map(_.as[Out](mapper))
//        |
//        |  def executeReactive($parameterList)(implicit session: CqlSession): Publisher[Out] =
//        |    apply($methodParameters).executeReactive().as[Out](mapper)
//        |
//        |  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2] =
//        |    new ScalaPreparedStatement$typeParameterCount(pstmt, mapper, $codecParams)
//        |}
//        |""".stripMargin
//
//    println(classTemplate)
//}
//
//(2 to 22).foreach(template)

class ScalaPreparedStatement2[T1, T2, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2])
    extends ScalaPreparedStatement[(T1, T2), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement2[T1, T2, T]

  override def tupled: ((T1, T2)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): AsOut[Out2] =
    new ScalaPreparedStatement2[T1, T2, Out2](pstmt, mapper, t1Codec, t2Codec)
}

// $COVERAGE-OFF$
class ScalaPreparedStatement3[T1, T2, T3, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3])
  extends ScalaPreparedStatement[(T1, T2, T3), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement3[T1, T2, T3, T]

  override def tupled: ((T1, T2, T3)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement3[T1, T2, T3, Out2] =
    new ScalaPreparedStatement3(pstmt, mapper, t1Codec, t2Codec, t3Codec)
}


class ScalaPreparedStatement4[T1, T2, T3, T4, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4])
  extends ScalaPreparedStatement[(T1, T2, T3, T4), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement4[T1, T2, T3, T4, T]

  override def tupled: ((T1, T2, T3, T4)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement4[T1, T2, T3, T4, Out2] =
    new ScalaPreparedStatement4(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec)
}


class ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement5[T1, T2, T3, T4, T5, T]

  override def tupled: ((T1, T2, T3, T4, T5)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out2] =
    new ScalaPreparedStatement5(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec)
}


class ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out2] =
    new ScalaPreparedStatement6(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec)
}


class ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out2] =
    new ScalaPreparedStatement7(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec)
}


class ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out2] =
    new ScalaPreparedStatement8(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec)
}


class ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out2] =
    new ScalaPreparedStatement9(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec)
}


class ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out2] =
    new ScalaPreparedStatement10(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec)
}


class ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out2] =
    new ScalaPreparedStatement11(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec)
}


class ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out2] =
    new ScalaPreparedStatement12(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec)
}


class ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out2] =
    new ScalaPreparedStatement13(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec)
}


class ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out2] =
    new ScalaPreparedStatement14(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec)
}


class ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out2] =
    new ScalaPreparedStatement15(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec)
}


class ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out2] =
    new ScalaPreparedStatement16(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec)
}


class ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out2] =
    new ScalaPreparedStatement17(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec)
}


class ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out2] =
    new ScalaPreparedStatement18(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec)
}


class ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out2] =
    new ScalaPreparedStatement19(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec)
}


class ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out2] =
    new ScalaPreparedStatement20(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec)
}


class ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20], t21Codec: TypeCodec[T21])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec).setIfDefined(20, t21, t21Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out2] =
    new ScalaPreparedStatement21(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec)
}


class ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20], t21Codec: TypeCodec[T21], t22Codec: TypeCodec[T22])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type AsOut[T] = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T]

  override def tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)) => BoundStatement = (apply _).tupled

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): BoundStatement =
    pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec).setIfDefined(20, t21, t21Codec).setIfDefined(21, t22, t22Codec)

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).execute().as[Out](mapper)

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).executeAsync().map(_.as[Out](mapper))

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).executeReactive().as[Out](mapper)

  def as[Out2](implicit mapper: RowMapper[Out2], ev: Out =:= Row): ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out2] =
    new ScalaPreparedStatement22(pstmt, mapper, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec, t22Codec)
}
// format: on
// $COVERAGE-ON$
