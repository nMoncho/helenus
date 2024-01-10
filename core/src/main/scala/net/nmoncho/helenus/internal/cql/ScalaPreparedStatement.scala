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

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter
import net.nmoncho.helenus.api.cql.PagerSerializer
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.BoundStatementOps
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.ScalaBoundStatement
import net.nmoncho.helenus.api.cql.StatementOptions
import net.nmoncho.helenus.api.cql.{ Pager => ApiPager }
import org.reactivestreams.Publisher

// format: off

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
class AdaptedScalaPreparedStatement[In2, In, Out](pstmt: ScalaPreparedStatement[In, _], mapper: RowMapper[Out], adapter: Adapter[In2, In], val options: StatementOptions)
    extends ScalaPreparedStatement[In2, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = AdaptedScalaPreparedStatement[In2, In, Out]
  override type AsOut[T] = AdaptedScalaPreparedStatement[In2, In, T]

  override val tupled: In2 => BoundStatement = apply

  /** Bounds an input [[In]] value and returns a [[BoundStatement]] */
  def apply(t1: In2): ScalaBoundStatement[Out] =
    tag(pstmt.tupled(adapter(t1)))

  /** Executes this [[PreparedStatement]] with the provided value.
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: In2)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion using
   * the provided [[In]] input value
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: In2)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: In2)(implicit session: CqlSession): Publisher[Out] =
    apply(t1).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new AdaptedScalaPreparedStatement[In2, In, Out2](pstmt, mapper, adapter, options)

  override def withOptions(options: StatementOptions): Self =
    new AdaptedScalaPreparedStatement(pstmt, mapper, adapter, options)

  def pager(t1: In2): ApiPager[Out] =
    Pager.initial(apply(t1))

  def pager(pagingState: PagingState, t1: In2): Try[ApiPager[Out]] =
    Pager.continue(apply(t1), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: In2): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1), pagingState)

}

/** A [[PreparedStatement]] without input parameters
 *
 * @param pstmt wrapped instance
 * @param mapper maps [[Row]]s into [[Out]] values
 * @tparam Out output value
 */
class ScalaPreparedStatementUnit[Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions)
    extends ScalaPreparedStatement[Unit, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatementUnit[Out]
  override type AsOut[T] = ScalaPreparedStatementUnit[T]

  override val tupled: Unit => BoundStatement = _ => apply

  verifyArity()

  /** Returns a [[BoundStatement]] */
  def apply(): ScalaBoundStatement[Out] = tag(applyOptions(pstmt.bind()))

  /** Executes this [[PreparedStatement]]
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute()(implicit session: CqlSession): PagingIterable[Out] =
    apply().execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync()(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply().executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive()(implicit session: CqlSession): Publisher[Out] =
    apply().executeReactive()

  /** Maps the result from this [[PreparedStatement]] with a different [[Out2]]
   * as long as there is an implicit [[RowMapper]] and [[Out]] is [[Row]] (this is
   * meant to avoid calling `as` twice)
   */
  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatementUnit[Out2](pstmt, mapper, options)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatementUnit(pstmt, mapper, options)

  def pager(): ApiPager[Out] = Pager.initial(apply())

  def pager(pagingState: PagingState): Try[ApiPager[Out]] =
    Pager.continue(apply(), pagingState)

  def pager[A: PagerSerializer](pagingState: A): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(), pagingState)

}

/** A [[PreparedStatement]] with one input parameter
 *
 * @param pstmt wrapped instance
 * @param mapper maps [[Row]]s into [[Out]] values
 * @param t1Codec how to encode [[T1]] values
 * @tparam T1 input value
 * @tparam Out output value
 */
class ScalaPreparedStatement1[T1, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1])
    extends ScalaPreparedStatement[T1, Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement1[T1, Out]
  override type AsOut[T] = ScalaPreparedStatement1[T1, T]

  override def tupled: T1 => BoundStatement = apply

  verifyArity(t1Codec)

  /** Bounds an input [[T1]] value and returns a [[BoundStatement]] */
  def apply(t1: T1): ScalaBoundStatement[Out] =
    tag[Out](applyOptions(pstmt.bind().setIfDefined(0, t1, t1Codec)))

  /** Executes this [[PreparedStatement]] with the provided value.
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion using
   * the provided [[T1]] input value
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1)(implicit session: CqlSession): Publisher[Out] =
    apply(t1).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement1(pstmt, mapper, options, t1Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement1(pstmt, mapper, options, t1Codec)

  def pager(t1: T1): ApiPager[Out] = Pager.initial(apply(t1))

  def pager(pagingState: PagingState, t1: T1): Try[ApiPager[Out]] =
    Pager.continue(apply(t1), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1), pagingState)

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
//        |class ScalaPreparedStatement$typeParameterCount[$typeParameters, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, $typeCodecs)
//        |    extends ScalaPreparedStatement[($typeParameters), Out](pstmt, mapper) {
//        |
//        |  import net.nmoncho.helenus._
//        |
//        |  override type Self     = ScalaPreparedStatement$typeParameterCount[$typeParameters, Out]
//        |  override type AsOut[T] = ScalaPreparedStatement$typeParameterCount[$typeParameters, T]
//        |
//        |  override val tupled: (($typeParameters)) => BoundStatement = (apply _).tupled
//        |
//        |  verifyArity($codecParams)
//        |
//        |  /** Returns a [[BoundStatement]] with the provided values*/
//        |  def apply($parameterList): ScalaBoundStatement[Out] =
//        |    tag(applyOptions(pstmt.bind()$parameterBindings))
//        |
//        |  /** Executes this [[PreparedStatement]] with the provided values
//        |   *
//        |   * @return [[PagingIterable]] of [[Out]] output values
//        |   */
//        |  def execute($parameterList)(implicit session: CqlSession): PagingIterable[Out] =
//        |    apply($methodParameters).execute()
//        |
//        |  /** Executes this [[PreparedStatement]] in a asynchronous fashion
//        |   *
//        |   * @return a future of [[MappedAsyncPagingIterable]]
//        |   */
//        |  def executeAsync($parameterList)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
//        |    apply($methodParameters).executeAsync()
//        |
//        |  /** Executes this [[PreparedStatement]] in a reactive fashion
//        |   *
//        |   * @return [[Publisher]] of [[Out]] output values
//        |   */
//        |  def executeReactive($parameterList)(implicit session: CqlSession): Publisher[Out] =
//        |    apply($methodParameters).executeReactive()
//        |
//        |  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
//        |    new ScalaPreparedStatement$typeParameterCount(pstmt, mapper, options, $codecParams)
//        |
//        |  override def withOptions(options: StatementOptions): Self =
//        |    new ScalaPreparedStatement$typeParameterCount(pstmt, mapper, options, $codecParams)
//        |
//        |  def pager($parameterList): Pager[Out] = Pager.initial(apply($methodParameters))
//        |
//        |  def pager[A: PagerSerializer](pagingState: A, $parameterList): Try[ApiPager[Out]] =
//        |    Pager.continue(apply($methodParameters), pagingState)
//        |
//        |
//        |}
//        |""".stripMargin
//
//    println(classTemplate)
//}
//
//(2 to 22).foreach(template)

class ScalaPreparedStatement2[T1, T2, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2])
  extends ScalaPreparedStatement[(T1, T2), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement2[T1, T2, Out]
  override type AsOut[T] = ScalaPreparedStatement2[T1, T2, T]

  override val tupled: ((T1, T2)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2): ScalaBoundStatement[Out] =
    tag(applyOptions(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec)))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement2(pstmt, mapper, options, t1Codec, t2Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement2(pstmt, mapper, options, t1Codec, t2Codec)

  def pager(t1: T1, t2: T2): ApiPager[Out] =
    Pager.initial(apply(t1, t2))

  def pager(pagingState: PagingState, t1: T1, t2: T2): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2), pagingState)

}

// $COVERAGE-OFF$
class ScalaPreparedStatement3[T1, T2, T3, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3])
  extends ScalaPreparedStatement[(T1, T2, T3), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement3[T1, T2, T3, Out]
  override type AsOut[T] = ScalaPreparedStatement3[T1, T2, T3, T]

  override val tupled: ((T1, T2, T3)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement3(pstmt, mapper, options, t1Codec, t2Codec, t3Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement3(pstmt, mapper, options, t1Codec, t2Codec, t3Codec)

  def pager(t1: T1, t2: T2, t3: T3): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3), pagingState)

}


class ScalaPreparedStatement4[T1, T2, T3, T4, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4])
  extends ScalaPreparedStatement[(T1, T2, T3, T4), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement4[T1, T2, T3, T4, Out]
  override type AsOut[T] = ScalaPreparedStatement4[T1, T2, T3, T4, T]

  override val tupled: ((T1, T2, T3, T4)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement4(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement4(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4), pagingState)

}


class ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]
  override type AsOut[T] = ScalaPreparedStatement5[T1, T2, T3, T4, T5, T]

  override val tupled: ((T1, T2, T3, T4, T5)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement5(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement5(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5), pagingState)

}


class ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]
  override type AsOut[T] = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement6(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement6(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6), pagingState)

}


class ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]
  override type AsOut[T] = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement7(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement7(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7), pagingState)

}


class ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]
  override type AsOut[T] = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement8(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement8(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8), pagingState)

}


class ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]
  override type AsOut[T] = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement9(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement9(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9), pagingState)

}


class ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]
  override type AsOut[T] = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement10(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement10(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10), pagingState)

}


class ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]
  override type AsOut[T] = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement11(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement11(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11), pagingState)

}


class ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]
  override type AsOut[T] = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement12(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement12(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12), pagingState)

}


class ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]
  override type AsOut[T] = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement13(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement13(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13), pagingState)

}


class ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]
  override type AsOut[T] = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement14(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement14(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14), pagingState)

}


class ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]
  override type AsOut[T] = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement15(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement15(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15), pagingState)

}


class ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]
  override type AsOut[T] = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement16(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement16(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16), pagingState)

}


class ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]
  override type AsOut[T] = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement17(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement17(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17), pagingState)

}


class ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]
  override type AsOut[T] = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement18(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement18(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18), pagingState)

}


class ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]
  override type AsOut[T] = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement19(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement19(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19), pagingState)

}


class ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]
  override type AsOut[T] = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement20(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement20(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20), pagingState)

}


class ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20], t21Codec: TypeCodec[T21])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]
  override type AsOut[T] = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec).setIfDefined(20, t21, t21Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement21(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement21(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21), pagingState)

}


class ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](pstmt: PreparedStatement, mapper: RowMapper[Out], val options: StatementOptions, t1Codec: TypeCodec[T1], t2Codec: TypeCodec[T2], t3Codec: TypeCodec[T3], t4Codec: TypeCodec[T4], t5Codec: TypeCodec[T5], t6Codec: TypeCodec[T6], t7Codec: TypeCodec[T7], t8Codec: TypeCodec[T8], t9Codec: TypeCodec[T9], t10Codec: TypeCodec[T10], t11Codec: TypeCodec[T11], t12Codec: TypeCodec[T12], t13Codec: TypeCodec[T13], t14Codec: TypeCodec[T14], t15Codec: TypeCodec[T15], t16Codec: TypeCodec[T16], t17Codec: TypeCodec[T17], t18Codec: TypeCodec[T18], t19Codec: TypeCodec[T19], t20Codec: TypeCodec[T20], t21Codec: TypeCodec[T21], t22Codec: TypeCodec[T22])
  extends ScalaPreparedStatement[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Out](pstmt, mapper) {

  import net.nmoncho.helenus._

  override type Self     = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]
  override type AsOut[T] = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, T]

  override val tupled: ((T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)) => BoundStatement = (apply _).tupled

  verifyArity(t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec, t22Codec)

  /** Returns a [[BoundStatement]] with the provided values*/
  def apply(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): ScalaBoundStatement[Out] =
    tag(pstmt.bind().setIfDefined(0, t1, t1Codec).setIfDefined(1, t2, t2Codec).setIfDefined(2, t3, t3Codec).setIfDefined(3, t4, t4Codec).setIfDefined(4, t5, t5Codec).setIfDefined(5, t6, t6Codec).setIfDefined(6, t7, t7Codec).setIfDefined(7, t8, t8Codec).setIfDefined(8, t9, t9Codec).setIfDefined(9, t10, t10Codec).setIfDefined(10, t11, t11Codec).setIfDefined(11, t12, t12Codec).setIfDefined(12, t13, t13Codec).setIfDefined(13, t14, t14Codec).setIfDefined(14, t15, t15Codec).setIfDefined(15, t16, t16Codec).setIfDefined(16, t17, t17Codec).setIfDefined(17, t18, t18Codec).setIfDefined(18, t19, t19Codec).setIfDefined(19, t20, t20Codec).setIfDefined(20, t21, t21Codec).setIfDefined(21, t22, t22Codec))

  /** Executes this [[PreparedStatement]] with the provided values
   *
   * @return [[PagingIterable]] of [[Out]] output values
   */
  def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): PagingIterable[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).execute()

  /** Executes this [[PreparedStatement]] in a asynchronous fashion
   *
   * @return a future of [[MappedAsyncPagingIterable]]
   */
  def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession, ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).executeAsync()

  /** Executes this [[PreparedStatement]] in a reactive fashion
   *
   * @return [[Publisher]] of [[Out]] output values
   */
  def executeReactive(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit session: CqlSession): Publisher[Out] =
    apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).executeReactive()

  override def as[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): AsOut[Out2] =
    new ScalaPreparedStatement22(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec, t22Codec)

  override def withOptions(options: StatementOptions): Self =
    new ScalaPreparedStatement22(pstmt, mapper, options, t1Codec, t2Codec, t3Codec, t4Codec, t5Codec, t6Codec, t7Codec, t8Codec, t9Codec, t10Codec, t11Codec, t12Codec, t13Codec, t14Codec, t15Codec, t16Codec, t17Codec, t18Codec, t19Codec, t20Codec, t21Codec, t22Codec)

  def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): ApiPager[Out] =
    Pager.initial(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

  def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): Try[ApiPager[Out]] =
    Pager.continue(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22), pagingState)

  def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22): Try[ApiPager[Out]] =
    Pager.continueFromEncoded(apply(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22), pagingState)

}
// format: on
// $COVERAGE-ON$
