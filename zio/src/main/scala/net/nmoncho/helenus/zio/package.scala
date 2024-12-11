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

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import _root_.zio.Chunk
import _root_.zio.ZIO
import _root_.zio.stream.ZSink
import _root_.zio.stream.ZStream
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.`type`.codec.CodecDerivation
import net.nmoncho.helenus.api.cql._
import net.nmoncho.helenus.internal.cql._
import net.nmoncho.helenus.zio.macros.ZIOCqlQueryInterpolation

package object zio extends CodecDerivation {

  type ZPagingIterable[Out] =
    ZIO[ZCqlSession, Throwable, PagingIterable[Out]]

  type ZAsyncPagingIterable[Out] =
    ZIO[ZCqlSession, Throwable, MappedAsyncPagingIterable[Out]]

  type ZCqlStream[Out] =
    ZStream[ZCqlSession, Throwable, Chunk[Out]]

  type ZWrappedBoundStatement[Out] =
    ZIO[ZCqlSession, Throwable, WrappedBoundStatement[Out]]

  type ZScalaPreparedStatement[In, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement[In, Out]]

  type ZAdaptedScalaPreparedStatement[In2, In, Out] =
    ZIO[ZCqlSession, Throwable, AdaptedScalaPreparedStatement[In2, In, Out]]

  type ZScalaPreparedStatementUnit[Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatementUnit[Out]]

  type ZScalaPreparedStatement1[T1, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement1[T1, Out]]

  type ZScalaPreparedStatement2[T1, T2, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement2[T1, T2, Out]]

  // format: off
  type ZScalaPreparedStatement3[T1, T2, T3, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement3[T1, T2, T3, Out]]

  type ZScalaPreparedStatement4[T1, T2, T3, T4, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement4[T1, T2, T3, T4, Out]]

  type ZScalaPreparedStatement5[T1, T2, T3, T4, T5, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]]

  type ZScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]]

  type ZScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]]

  type ZScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]]

  type ZScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]]

  type ZScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]]

  type ZScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]]

  type ZScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]]

  type ZScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]]

  type ZScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]]

  type ZScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]]

  type ZScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]]

  type ZScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]]

  type ZScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]]

  type ZScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]]

  type ZScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]]

  type ZScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]]

  type ZScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out] =
    ZIO[ZCqlSession, Throwable, ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]]

  // format: on

  implicit class PreparedStatementSyncStringOps(private val query: String) extends AnyVal {

    /** Converts a simple string to a [[ZCQLQuery]] which can then be prepared
      *
      * {{{
      * import net.nmoncho.helenus.zio._
      *
      * val id = UUID.fromString("...")
      * val bstmt = "SELECT * FROM some_table WHERE id = $id".toZCQL.prepare[UUID].exectue(id)
      * }}}
      *
      * @return [[ZCQLQuery]] wrapping the provided string
      */
    def toZCQL: ZCQLQuery = new ZCQLQuery(query)

  }

  private def executeStatement[In, Out: RowMapper](prepared: ZIO[ZCqlSession, Throwable, In])(
      fn: In => ScalaBoundStatement[Out]
  ): ZPagingIterable[Try[Out]] = for {
    session <- ZIO.service[ZCqlSession]
    pstmt <- prepared
    result <- session.execute(fn(pstmt))
  } yield result

  private def executeStatementAsync[In, Out: RowMapper](prepared: ZIO[ZCqlSession, Throwable, In])(
      fn: In => ScalaBoundStatement[Out]
  ): ZAsyncPagingIterable[Try[Out]] = for {
    session <- ZIO.service[ZCqlSession]
    pstmt <- prepared
    result <- session.executeAsync(fn(pstmt))
  } yield result

  private def executeStream[Out](pi: ZAsyncPagingIterable[Try[Out]]): ZCqlStream[Try[Out]] =
    ZStream
      .fromZIO(pi)
      .flatMap(paginate(_).map(page => Chunk.fromIterator(page.currPage)))

  private def executeStreamValidated[Out](stream: ZCqlStream[Try[Out]]): ZCqlStream[Out] =
    stream.mapZIO(page =>
      ZIO.validate(page)(ZIO.fromTry(_)).mapError {
        case (iae: IllegalArgumentException) :: _ =>
          new InvalidMappingException(
            "Something went wrong while trying to decode a row",
            iae
          )

        case (t: Throwable) :: _ =>
          new GenericCassandraException(
            "Something went wrong while getting the next row",
            t
          )
      }
    )

  private def executeSink[In, A](prepared: ZIO[ZCqlSession, Throwable, In])(
      fn: In => A => BoundStatement
  ): ZSink[ZCqlSession, Throwable, A, Nothing, Unit] = ZSink.foreach { in =>
    for {
      session <- ZIO.service[ZCqlSession]
      pstmt <- prepared.map(fn)
      _ <- session.executeAsyncFromJava(pstmt(in))
    } yield ()
  }

  private def paginate[Out](
      initial: MappedAsyncPagingIterable[Out]
  ): ZStream[Any, Throwable, MappedAsyncPagingIterable[Out]] =
    ZStream.paginateZIO(initial) { current =>
      if (current.hasMorePages) {
        ZIO.fromCompletionStage(current.fetchNextPage()).map(next => current -> Some(next))
      } else {
        ZIO.succeed(current -> None)
      }
    }

  import scala.language.experimental.macros

  /** Creates a [[BoundStatement]] using String Interpolation.
    * There is also an asynchronous alternative, which is `zcqlAsync` instead of `zcql`.
    *
    * This won't execute the bound statement yet, just set its arguments.
    *
    * {{{
    * import net.nmoncho.helenus.zio._
    *
    * val id = UUID.fromString("...")
    * val bstmt = zcql"SELECT * FROM some_table WHERE id = $id"
    * }}}
    */
  implicit class ZIOCqlStringInterpolation(private val sc: StringContext) extends AnyVal {

    def zcql(params: Any*): ZWrappedBoundStatement[Row] =
      macro ZIOCqlQueryInterpolation.zcql

    def zcqlAsync(params: Any*): ZWrappedBoundStatement[Row] =
      macro ZIOCqlQueryInterpolation.zcql
  }

  implicit class ZWrappedBoundStatementOps[Out](
      private val bstmt: ZWrappedBoundStatement[Out]
  ) extends AnyVal {

    def to[Out2](implicit ev: Out =:= Row, m: RowMapper[Out2]): ZWrappedBoundStatement[Out2] =
      bstmt.map(_.as[Out2])

    def execute()(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(bstmt)(_.asInstanceOf[ScalaBoundStatement[Out]])

    def executeAsync()(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(bstmt)(_.asInstanceOf[ScalaBoundStatement[Out]])

    def stream()(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync())

    def streamValidated()(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream())
  }

  implicit class ZScalaPreparedStatementOps[In, Out](
      private val pstmt: ZScalaPreparedStatement[In, Out]
  ) extends AnyVal {

    def from[In2](implicit a: Adapter[In2, In]): ZAdaptedScalaPreparedStatement[In2, In, Out] =
      pstmt.map(_.from)

    def sink(): ZSink[ZCqlSession, Throwable, In, Nothing, Unit] =
      executeSink(pstmt)(_.tupled)

  }

  implicit class ZAdaptedScalaPreparedStatementOps[In2, In, Out](
      private val pstmt: ZAdaptedScalaPreparedStatement[In2, In, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZAdaptedScalaPreparedStatement[In2, In, O]
    type Self     = AdaptedScalaPreparedStatement[In2, In, Out]
    type Opts     = Options[In2, Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(in: In2)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(in))

    def executeAsync(in: In2)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(in))

    def stream(in: In2)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(in))

    def streamValidated(in: In2)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(in))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatementUnitOps[Out](
      private val pstmt: ZScalaPreparedStatementUnit[Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatementUnit[O]
    type Self     = ScalaPreparedStatementUnit[Out]
    type Opts     = Options[Unit, Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute()(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_())

    def executeAsync()(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_())

    def stream()(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync())

    def streamValidated()(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream())

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement1Ops[T1, Out](
      private val pstmt: ZScalaPreparedStatement1[T1, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement1[T1, O]
    type Self     = ScalaPreparedStatement1[T1, Out]
    type Opts     = Options[T1, Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1))

    def executeAsync(t1: T1)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1))

    def stream(t1: T1)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1))

    def streamValidated(t1: T1)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement2Ops[T1, T2, Out](
      private val pstmt: ZScalaPreparedStatement2[T1, T2, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement2[T1, T2, O]
    type Self     = ScalaPreparedStatement2[T1, T2, Out]
    type Opts     = Options[(T1, T2), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2))

    def executeAsync(t1: T1, t2: T2)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2))

    def stream(t1: T1, t2: T2)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2))

    def streamValidated(t1: T1, t2: T2)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  // $COVERAGE-OFF$
  // format: off
  implicit class ZScalaPreparedStatement3Ops[T1, T2, T3, Out](
      private val pstmt: ZScalaPreparedStatement3[T1, T2, T3, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement3[T1, T2, T3, O]
    type Self     = ScalaPreparedStatement3[T1, T2, T3, Out]
    type Opts     = Options[(T1, T2, T3), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3))

    def executeAsync(t1: T1, t2: T2, t3: T3)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3))

    def stream(t1: T1, t2: T2, t3: T3)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3))

    def streamValidated(t1: T1, t2: T2, t3: T3)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement4Ops[T1, T2, T3, T4, Out](
      private val pstmt: ZScalaPreparedStatement4[T1, T2, T3, T4, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement4[T1, T2, T3, T4, O]
    type Self     = ScalaPreparedStatement4[T1, T2, T3, T4, Out]
    type Opts     = Options[(T1, T2, T3, T4), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement5Ops[T1, T2, T3, T4, T5, Out](
      private val pstmt: ZScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement5[T1, T2, T3, T4, T5, O]
    type Self     = ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement6Ops[T1, T2, T3, T4, T5, T6, Out](
      private val pstmt: ZScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, O]
    type Self     = ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement7Ops[T1, T2, T3, T4, T5, T6, T7, Out](
      private val pstmt: ZScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, O]
    type Self     = ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement8Ops[T1, T2, T3, T4, T5, T6, T7, T8, Out](
      private val pstmt: ZScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, O]
    type Self     = ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement9Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](
      private val pstmt: ZScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, O]
    type Self     = ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement10Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](
      private val pstmt: ZScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, O]
    type Self     = ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement11Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](
      private val pstmt: ZScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, O]
    type Self     = ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), Out]

    def to[Out2](implicit ev: Out =:= Row, m: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement12Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](
      private val pstmt: ZScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, O]
    type Self     = ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }
  
  implicit class ZScalaPreparedStatement13Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](
      private val pstmt: ZScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]
  ) extends AnyVal {

    type ZSelf[O]        = ZScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, O]
    type Self = ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement14Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](
      private val pstmt: ZScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, O]
    type Self     = ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement15Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](
      private val pstmt: ZScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, O]
    type Self     = ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement16Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](
      private val pstmt: ZScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, O]
    type Self     = ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement17Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](
      private val pstmt: ZScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, O]
    type Self     = ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement18Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](
      private val pstmt: ZScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, O]
    type Self     = ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement19Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](
      private val pstmt: ZScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, O]
    type Self     = ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement20Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](
      private val pstmt: ZScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, O]
    type Self     = ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement21Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](
      private val pstmt: ZScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, O]
    type Self     = ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }

  implicit class ZScalaPreparedStatement22Ops[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](
      private val pstmt: ZScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]
  ) extends AnyVal {

    type ZSelf[O] = ZScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, O]
    type Self     = ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]
    type Opts     = Options[(T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22), Out]

    def to[Out2](implicit ev: Out =:= Row, mapper: RowMapper[Out2]): ZSelf[Out2] =
      pstmt.map(_.as[Out2])

    def execute(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit m: RowMapper[Out]): ZPagingIterable[Try[Out]] =
      executeStatement(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit m: RowMapper[Out]): ZAsyncPagingIterable[Try[Out]] =
      executeStatementAsync(pstmt)(_(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

    def stream(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit m: RowMapper[Out]): ZCqlStream[Try[Out]] =
      executeStream(executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

    def streamValidated(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit m: RowMapper[Out]): ZCqlStream[Out] =
      executeStreamValidated(stream(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

    def withOptions(options: StatementOptions): ZSelf[Out] =
      pstmt.map(_.withOptions(options))

    def withOptions(fn: Opts => Opts): ZSelf[Out] =
      pstmt.map(fn(_).asInstanceOf[Self])
  }
  // format: on
  // $COVERAGE-ON$

  /** Extension methods for [[PagingIterable]]
    *
    * Mostly how to transform this Cassandra iterable into a more Scala idiomatic structure.
    */
  implicit class ZPagingIterableOps[Out](private val pi: ZPagingIterable[Try[Out]]) extends AnyVal {
    import scala.collection.compat._

    /** Returns the next element, or [[None]] if the iterable is exhausted.
      *
      * <p>This is convenient for queries that are known to return exactly one row, for example count
      * queries.
      */
    def oneOption: ZIO[ZCqlSession, Throwable, Option[Out]] = pi.flatMap { underlying =>
      underlying.nextOption() match {
        case Some(Success(value)) =>
          ZIO.succeed(Some(value))

        case Some(Failure(iae: IllegalArgumentException)) =>
          ZIO.fail(
            new InvalidMappingException("Something went wrong while trying to decode a row", iae)
          )

        case Some(Failure(t)) =>
          ZIO.fail(
            new GenericCassandraException("Something went wrong while getting the next row", t)
          )

        case None =>
          ZIO.succeed(None)
      }
    }

    /** This [[PagingIterable]] as a Scala Collection; <b>not recommended for queries that return a
      * large number of elements</b>.
      *
      * Example
      * {{{
      *   import scala.collection.compat._ // Only for Scala 2.12
      *
      *   pagingIterable.to(List)
      *   pagingIterable.to(Set)
      * }}}
      */
    def to[Col[_]](factory: Factory[Out, Col[Out]])(
        implicit cbf: BuildFrom[Nothing, Out, Col[Out]]
    ): ZIO[ZCqlSession, Throwable, Col[Out]] =
      pi.flatMap { underlying =>
        val it = underlying.iter

        ZIO
          .iterate(factory.newBuilder)(_ => it.hasNext) { builder =>
            it.next() match {
              case Success(value) =>
                builder += value
                ZIO.succeed(builder)

              case Failure(iae: IllegalArgumentException) =>
                ZIO.fail(
                  new InvalidMappingException(
                    "Something went wrong while trying to decode a row",
                    iae
                  )
                )

              case Failure(t) =>
                ZIO.fail(
                  new GenericCassandraException(
                    "Something went wrong while getting the next row",
                    t
                  )
                )

            }
          }
          .map(_.result())
      }

    /** This [[PagingIterable]] as a Scala [[Iterator]]
      *
      * Under the hood the DSE Driver will poll more pages when
      * the iterator has more elements to be provided, which can
      * throw errors (e.g. connection lost).
      *
      * If you know the result size is small, consider using the `.to(Coll)` method.
      * Otherwise consider using async execution where you can control the pages within ZIO
      */
    def iterator: ZIO[ZCqlSession, Throwable, Iterator[Try[Out]]] =
      pi.map(_.iter)

  }

  implicit class ZMappedAsyncPagingIterableOps[Out](
      private val zpi: ZAsyncPagingIterable[Try[Out]]
  ) extends AnyVal {
    import scala.collection.compat._

    // format: off
    /** Fetches the current page after execution as a Scala [[Iterator]]
     *
     * Unlike the synchronous page, this iterator <em>won't</em> fetch more pages
     * when it runs out of records. To do that, use the `fetchNextPage()` method
     */
    def currentAndNextPage: ZIO[ZCqlSession, Throwable, (Iterator[Try[Out]], ZAsyncPagingIterable[Try[Out]])] =
      zpi.map(_.currPage -> fetchNextPage)
    // format: on

    /** Fetches next page */
    def fetchNextPage: ZAsyncPagingIterable[Try[Out]] =
      zpi.flatMap(fetchNextPage)

    private def fetchNextPage(underlying: MappedAsyncPagingIterable[Try[Out]]) =
      ZIO.fromCompletionStage(underlying.fetchNextPage())

    /** Returns the next element from the results.
      *
      * Use this method when you <em>only<em> care about the first element.
      *
      * Unlike [[nextOption]], this method doesn't expose the mutated [[MappedAsyncPagingIterable]],
      * so it's not meant for iterating through all results.
      *
      * @return [[Some]] value if results haven't been exhausted, [[None]] otherwise
      */
    def oneOption: ZIO[ZCqlSession, Throwable, Option[Out]] = zpi.flatMap { underlying =>
      underlying.oneOption match {
        case Some(Success(value)) =>
          ZIO.succeed(Some(value))

        case Some(Failure(iae: IllegalArgumentException)) =>
          ZIO.fail(
            new InvalidMappingException("Something went wrong while trying to decode a row", iae)
          )

        case Some(Failure(t)) =>
          ZIO.fail(
            new GenericCassandraException("Something went wrong while getting the next row", t)
          )

        case None =>
          ZIO.succeed(None)
      }
    }

    // format: off
    /** Returns the next element from the results.
      *
      * It also returns the [[MappedAsyncPagingIterable]] that should be used next, since this could be the
      * last element from the page. A [[MappedAsyncPagingIterable]] effectively represents a pagination mechanism
      *
      * This is convenient for queries that are known to return exactly one element, for example
      * count queries.
      *
      * @return [[Some]] value if results haven't been exhausted, [[None]] otherwise
      */
    def nextOption: ZIO[ZCqlSession, Throwable, (Option[Out], ZAsyncPagingIterable[Try[Out]])] = {
      zpi.flatMap { underlying =>
        underlying.oneOption match {
          case Some(Success(value)) =>
            ZIO.succeed(Some(value) -> ZIO.succeed(underlying))

          case Some(Failure(iae: IllegalArgumentException)) =>
            ZIO.fail(
              new InvalidMappingException("Something went wrong while trying to decode a row", iae)
            )

          case Some(Failure(t)) =>
            ZIO.fail(
              new GenericCassandraException("Something went wrong while getting the next row", t)
            )

          case None if underlying.hasMorePages =>
            fetchNextPage(underlying).nextOption

          case _ =>
            ZIO.succeed(None -> zpi)
        }
      }
    }
    // format: on

    /** This [[PagingIterable]] as a Scala Collection; <b>not recommended for queries that return a
      * large number of elements</b>.
      *
      * Example
      * {{{
      *   import scala.collection.compat._ // Only for Scala 2.12
      *
      *   pagingIterable.to(List)
      *   pagingIterable.to(Set)
      * }}}
      */
    def to[Col[_]](factory: Factory[Out, Col[Out]])(
        implicit cbf: BuildFrom[Nothing, Out, Col[Out]]
    ): ZIO[ZCqlSession, Throwable, Col[Out]] =
      zpi.flatMap { underlying =>
        val it = underlying.currentPage().iterator()

        ZIO
          .iterate(factory.newBuilder)(_ => it.hasNext) { builder =>
            it.next() match {
              case Success(value) =>
                builder += value
                ZIO.succeed(builder)

              case Failure(iae: IllegalArgumentException) =>
                ZIO.fail(
                  new InvalidMappingException(
                    "Something went wrong while trying to decode a row",
                    iae
                  )
                )

              case Failure(t) =>
                ZIO.fail(
                  new GenericCassandraException(
                    "Something went wrong while getting the next row",
                    t
                  )
                )

            }
          }
          .flatMap { builder =>
            if (underlying.hasMorePages) {
              fetchNextPage(underlying)
                .to(factory)
                .map(tail =>
                  (builder ++= tail.asInstanceOf[scala.collection.Iterable[Out]]).result()
                )
            } else {
              ZIO.succeed(builder.result())
            }
          }
      }
  }

}
