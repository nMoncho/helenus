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

package net.nmoncho

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros
import scala.util.Failure
import scala.util.Try

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql.AsyncResultSet
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PagingState
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.`type`.codec.CodecDerivation
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.api.cql.{ Pager => ApiPager, _ }
import net.nmoncho.helenus.internal.codec.udt.UDTCodec
import net.nmoncho.helenus.internal.cql._
import net.nmoncho.helenus.internal.macros.CqlQueryInterpolation
import net.nmoncho.helenus.internal.reactive.MapOperator
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

package object helenus extends CodecDerivation {

  type TaggedBoundStatement[Out] = { type Tag = Out }
  type ScalaBoundStatement[Out]  = BoundStatement with TaggedBoundStatement[Out]

  private val log = LoggerFactory.getLogger("net.nmoncho.helenus")

  implicit def cqlSessionAdapter(implicit session: CqlSession): Future[CqlSession] =
    Future.successful(session)

  implicit val defaultIdentityRowMapper: RowMapper[Row] = RowMapper.identity

  implicit class ClqSessionOps(private val session: CqlSession) extends AnyVal {

    def sessionKeyspace: Option[KeyspaceMetadata] = {
      val opt: java.util.Optional[String] = session.getKeyspace.map(_.asInternal())

      if (opt.isPresent) keyspace(opt.get())
      else None
    }

    def keyspace(name: String): Option[KeyspaceMetadata] = {
      val opt = session.getMetadata.getKeyspace(name)

      if (opt.isPresent) Some(opt.get())
      else None
    }

    /** Gets a [[DriverExecutionProfile]] from this [[CqlSession]]'s config
      *
      * @param name profile name
      * @return some [[DriverExecutionProfile]] if found, [[None]] otherwise
      */
    def executionProfile(name: String): Option[DriverExecutionProfile] =
      Try(session.getContext.getConfig.getProfile(name)).recoverWith { case t: Throwable =>
        log.warn("Couldn't find execution profile with name [{}]", name, t: Any)
        Failure(t)
      }.toOption

    /** Registers codecs in the Session's CodecRegistry.
      */
    def registerCodecs(codecs: TypeCodec[_]*): Try[Unit] =
      session.getContext.getCodecRegistry match {
        case mutableRegistry: MutableCodecRegistry =>
          Try {
            val keyspace = session.sessionKeyspace

            codecs.foreach {
              case codec: UDTCodec[_] if codec.isKeyspaceBlank =>
                keyspace
                  .map(k => codec.forKeyspace(k.getName.asInternal()))
                  .orElse {
                    log.warn(
                      "Codec [{}] won't be registered since it's not pointing to a Keyspace, and the session is not connected either",
                      codec
                    )
                    None
                  }
                  .foreach(codec => mutableRegistry.register(codec))

              case codec =>
                mutableRegistry.register(codec)
            }
          }

        // $COVERAGE-OFF$
        case _ =>
          Failure(new IllegalStateException("CodecRegistry isn't mutable"))
        // $COVERAGE-ON$
      }

  }

  /** Creates a [[BoundStatement]] using String Interpolation.
    * There is also an asynchronous alternative, which is `asyncCql` instead of `cql`.
    *
    * This won't execute the bound statement yet, just set its arguments.
    *
    * {{{
    * import net.nmoncho.helenus._
    *
    * val id = UUID.fromString("...")
    * val bstmt = cql"SELECT * FROM some_table WHERE id = $id"
    * }}}
    */
  implicit class CqlStringInterpolation(private val sc: StringContext) extends AnyVal {

    def cql(params: Any*)(implicit session: CqlSession): WrappedBoundStatement[Row] =
      macro CqlQueryInterpolation.cql

    def cqlAsync(
        params: Any*
    )(
        implicit session: Future[CqlSession],
        ec: ExecutionContext
    ): Future[WrappedBoundStatement[Row]] =
      macro CqlQueryInterpolation.cqlAsync

  }

  @inline private[helenus] def tag[Out](bs: BoundStatement): ScalaBoundStatement[Out] =
    bs.asInstanceOf[ScalaBoundStatement[Out]]

  implicit class BoundStatementOps(private val bs: BoundStatement) extends AnyVal {

    /** Sets or binds the specified value only if it's not NULL, avoiding a tombstone insert.
      *
      * @param index position of bound parameter
      * @param value value to be bound
      * @param codec how to encode the provided value
      * @tparam T
      * @return a modified version of this [[BoundStatement]]
      */
    @inline private[helenus] def setIfDefined[T](
        index: Int,
        value: T,
        codec: TypeCodec[T]
    ): BoundStatement =
      if (value == null || value == None) bs else bs.set(index, value, codec)
  }

  /** Extension methods for [[BoundStatement]], helping you execute them with the proper context.
    */
  implicit class BoundStatementSyncOps[Out](private val bstmt: ScalaBoundStatement[Out])
      extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    def execute()(implicit session: CqlSession, mapper: RowMapper[Out]): PagingIterable[Out] =
      session.execute(bstmt).as[Out]

    def executeAsync()(
        implicit session: CqlSession,
        ec: ExecutionContext,
        mapper: RowMapper[Out]
    ): Future[MappedAsyncPagingIterable[Out]] =
      session.executeAsync(bstmt).asScala.map(_.as[Out])

    def executeReactive()(implicit session: CqlSession, mapper: RowMapper[Out]): Publisher[Out] =
      session.executeReactive(bstmt).as[Out]

    def pager(implicit mapper: RowMapper[Out]): Pager[Out] =
      Pager.initial(bstmt)

    def pager(pagingState: PagingState)(implicit mapper: RowMapper[Out]): Try[Pager[Out]] =
      Pager.continue(bstmt, pagingState)

    def pager[A: PagerSerializer](pagingState: A)(
        implicit mapper: RowMapper[Out]
    ): Try[Pager[Out]] =
      Pager.continueFromEncoded(bstmt, pagingState)

    /** Set options to this [[BoundStatement]] while returning the original type
      */
    def withOptions(fn: BoundStatement => BoundStatement): ScalaBoundStatement[Out] =
      fn(bstmt).asInstanceOf[ScalaBoundStatement[Out]]

    /** Set options to this [[BoundStatement]] while returning the original type
      */
    def withOptions(options: StatementOptions): ScalaBoundStatement[Out] =
      options(bstmt).asInstanceOf[ScalaBoundStatement[Out]]
  }

  implicit class PreparedStatementSyncStringOps(private val query: String) extends AnyVal {

    def toCQL(implicit session: CqlSession): CQLQuery =
      CQLQuery(
        query,
        session
      )

    def toCQLAsync(
        implicit futSession: Future[CqlSession],
        ec: ExecutionContext
    ): Future[CQLQuery] =
      futSession.map(CQLQuery(query, _))
  }

  implicit class RowOps(private val row: Row) extends AnyVal {

    /** Converts a [[Row]] into a [[T]]
      */
    def as[T](implicit mapper: RowMapper[T]): T = mapper.apply(row)

    /** Gets a column from this [[Row]] of type [[T]] by name
      *
      * @param name column name
      */
    def getCol[T](name: String)(implicit codec: TypeCodec[T]): T = row.get(name, codec)

    /** Gets a column from this [[Row]] of type [[T]] by index
      *
      * @param index position in row
      */
    def getCol[T](index: Int)(implicit codec: TypeCodec[T]): T = row.get(index, codec)
  }

  implicit class ResultSetOps(private val rs: ResultSet) extends AnyVal {

    /** Converts a [[ResultSet]] into a [[PagingIterable]] of type [[T]]
      */
    def as[T](implicit mapper: RowMapper[T]): PagingIterable[T] = rs.map(mapper.apply)
  }

  implicit class AsyncResultSetOps(private val rs: AsyncResultSet) extends AnyVal {

    /** Converts a [[AsyncResultSet]] into a [[MappedAsyncPagingIterable]] of type [[T]]
      */
    def as[T](implicit mapper: RowMapper[T]): MappedAsyncPagingIterable[T] = rs.map(mapper.apply)
  }

  implicit class ReactiveResultSetOpt(private val rrs: ReactiveResultSet) extends AnyVal {

    /** Converts a [[ReactiveResultSet]] into a [[Publisher]] of type [[T]]
      */
    def as[T](implicit mapper: RowMapper[T]): Publisher[T] = {
      val op = new MapOperator(rrs, mapper.apply)

      op.publisher
    }
  }

  /** Extension methods for [[PagingIterable]]
    *
    * Mostly how to transform this Cassandra iterable into a more Scala idiomatic structure.
    */
  implicit class PagingIterableOps[T](private val pi: PagingIterable[T]) extends AnyVal {
    import scala.collection.compat._

    /** Next potential element of this iterable
      */
    def nextOption(): Option[T] = Option(pi.one())

    /** This [[PagingIterable]] as a Scala [[Iterator]]
      */
    def iter: Iterator[T] = {
      import scala.jdk.CollectionConverters._

      pi.iterator().asScala
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
    def to[Col[_]](factory: Factory[T, Col[T]])(
        implicit cbf: BuildFrom[Nothing, T, Col[T]]
    ): Col[T] = iter.to(factory)
  }

  implicit class MappedAsyncPagingIterableOps[T](private val pi: MappedAsyncPagingIterable[T])
      extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    import scala.jdk.CollectionConverters._

    /** Current page as a Scala [[Iterator]]
      */
    def currPage: Iterator[T] = pi.currentPage().iterator().asScala

    /** Fetches and returns the next page as a Scala [[Iterator]]
      */
    def nextPage(
        implicit ec: ExecutionContext
    ): Future[Option[(Iterator[T], MappedAsyncPagingIterable[T])]] =
      if (pi.hasMorePages) {
        pi.fetchNextPage().asScala.map(pi => Some(pi.currPage -> pi))
      } else {
        Future.successful(None)
      }

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
    def nextOption()(
        implicit ec: ExecutionContext
    ): Future[Option[(T, MappedAsyncPagingIterable[T])]] = {
      val fromCurrentPage = pi.one()

      if (fromCurrentPage != null) {
        Future.successful(Some(fromCurrentPage -> pi))
      } else if (pi.hasMorePages) {
        pi.fetchNextPage().asScala.map(nextPage => Option(nextPage.one()).map(_ -> nextPage))
      } else {
        Future.successful(None)
      }
    }

    /** Returns the next element from the results
      *
      * It also returns the [[MappedAsyncPagingIterable]] that should be used next, since this could be the
      * last element from the page. A [[MappedAsyncPagingIterable]] effectively represents a pagination mechanism
      *
      * This is convenient for queries that are known to return exactly one element, for example
      * count queries.
      *
      * It will fetch the next page in a blocking fashion after it has exhausted the current page.
      *
      * @param timeout how much time to wait for the next page to be ready
      * @return [[Some]] value if results haven't been exhausted, [[None]] otherwise
      */
    def nextOption(
        timeout: FiniteDuration
    )(implicit ec: ExecutionContext): Option[(T, MappedAsyncPagingIterable[T])] = {
      val fromCurrentPage = pi.one()

      if (fromCurrentPage != null) {
        Some(fromCurrentPage -> pi)
      } else if (pi.hasMorePages) {
        log.debug("Fetching more pages for request [{}]", pi.getExecutionInfo.getRequest)
        Await.result(
          pi.fetchNextPage().asScala.map(nextPage => Option(nextPage.one()).map(_ -> nextPage)),
          timeout
        )
      } else {
        None
      }
    }

    /** Return all results of this [[MappedAsyncPagingIterable]] as a Scala [[Iterator]],
      * without having to request more pages.
      *
      * It will fetch the next page in a blocking fashion after it has exhausted the current page.
      * <b>NOTE:</b> On Scala 2.12 it will fetch all pages!
      *
      * @param timeout how much time to wait for the next page to be ready
      * @param ec
      */
    @nowarn("cat=unused-imports")
    def iter(timeout: FiniteDuration)(implicit ec: ExecutionContext): Iterator[T] = {
      import scala.collection.compat._ // Don't remove me
      // FIXME Using `TraversableOnce` Scala 2.12, also it doesn't lazily concat iterators
      // since `compat` implementation is different
      def concat(): TraversableOnce[T] =
        pi
          .currentPage()
          .iterator()
          .asScala
          .concat {
            if (pi.hasMorePages) {
              log.debug("fetching more pages")
              Await.ready(pi.fetchNextPage().asScala, timeout)

              concat()
            } else {
              log.debug("no more pages")
              Iterator()
            }
          }

      concat().iterator
    }
  }

  /** [[PagingState]] Extension Methods
    */
  implicit class PagingStateOps(private val pagingState: PagingState) extends AnyVal {

    /** Encodes this [[PagingState]] provided there is an implicit [[PagerSerializer]]
      */
    def encode()(implicit serializer: PagerSerializer[_]): Try[serializer.SerializedState] =
      serializer.serialize(pagingState)

  }

  implicit class AsyncScalaPreparedStatementWithResultAdapterOps[In, Out](
      private val fut: Future[ScalaPreparedStatement[In, Out]]
  ) extends AnyVal {

    /** Adapts this [[ScalaPreparedStatement]] converting [[In2]] values with the provided adapter
      * into a [[In]] value (ie. the original type of this statement)
      *
      * @param adapter how to adapt an [[In2]] value into [[In]] value
      * @tparam In2 new input type
      * @return adapted [[ScalaPreparedStatement]] with new [[In2]] input type
      */
    def from[In2](
        implicit ec: ExecutionContext,
        adapter: Adapter[In2, In]
    ): Future[AdaptedScalaPreparedStatement[In2, In, Out]] =
      fut.map(_.from[In2])

    /** Adapts this [[ScalaPreparedStatement]] converting [[In2]] values with the provided adapter
      * into a [[In]] value (ie. the original type of this statement)
      *
      * @param adapter how to adapt an [[In2]] value into [[In]] value
      * @tparam In2 new input type
      * @return adapted [[ScalaPreparedStatement]] with new [[In2]] input type
      */
    def from[In2](adapter: In2 => In)(
        implicit ec: ExecutionContext
    ): Future[AdaptedScalaPreparedStatement[In2, In, Out]] =
      fut.map(_.from((a: In2) => adapter(a)))
  }

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  // def template(typeParameterCount: Int): Unit = {
  //  val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //  val typeCodecs = (1 to typeParameterCount).map(i => s"T$i: TypeCodec").mkString(", ")
  //  val className = s"ScalaPreparedStatement$typeParameterCount[$typeParams, Row]"
  //
  //  println(s"def prepare[$typeCodecs](implicit ec: ExecutionContext): Future[ScalaPreparedStatement$typeParameterCount[$typeParams, Row]] = cql.map(_.prepare[$typeParams])\n")
  // }
  //
  //
  // (2 to 22).foreach(template)

  // format: off
  // $COVERAGE-OFF$
  implicit class CQLAsyncOps(private val cql: Future[CQLQuery]) extends AnyVal {
    def prepareUnit(implicit ec: ExecutionContext): Future[ScalaPreparedStatementUnit[Row]] = cql.map(_.prepareUnit)

    def prepareFrom[T1: Mapping](implicit ec: ExecutionContext): Future[ScalaPreparedStatementMapped[T1, Row]] = cql.map(_.prepareFrom[T1])

    def prepare[T1: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement1[T1, Row]] = cql.map(_.prepare[T1])

    def prepare[T1: TypeCodec, T2: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement2[T1, T2, Row]] = cql.map(_.prepare[T1, T2])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement3[T1, T2, T3, Row]] = cql.map(_.prepare[T1, T2, T3])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Row]] = cql.map(_.prepare[T1, T2, T3, T4])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec, T21: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21])

    def prepare[T1: TypeCodec, T2: TypeCodec, T3: TypeCodec, T4: TypeCodec, T5: TypeCodec, T6: TypeCodec, T7: TypeCodec, T8: TypeCodec, T9: TypeCodec, T10: TypeCodec, T11: TypeCodec, T12: TypeCodec, T13: TypeCodec, T14: TypeCodec, T15: TypeCodec, T16: TypeCodec, T17: TypeCodec, T18: TypeCodec, T19: TypeCodec, T20: TypeCodec, T21: TypeCodec, T22: TypeCodec](implicit ec: ExecutionContext): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row]] = cql.map(_.prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22])
  }
  // format: on

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  // def template(typeParameterCount: Int): Unit = {
  //  val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //  val typeCodecs = (1 to typeParameterCount).map(i => s"t$i: TypeCodec[T$i]").mkString(", ")
  //  val className = s"ScalaPreparedStatement$typeParameterCount[$typeParams, Row]"
  //  val actualParams = (1 to typeParameterCount).map(i => s"t$i").mkString(", ")
  //  val formalParams = (1 to typeParameterCount).map(i => s"t$i: T$i").mkString(", ")
  //
  //  println(s"""implicit class AsyncAsPreparedStatement$typeParameterCount[$typeParams, Out](private val fut: Future[ScalaPreparedStatement$typeParameterCount[$typeParams, Out]]) extends AnyVal {
  //  |  def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement$typeParameterCount[$typeParams, Out2]] = fut.map(_.as[Out2])
  //  |
  //  |  def executeAsync($formalParams)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap { implicit s => fut.flatMap(_.executeAsync($actualParams)) }
  //  |}
  //  |""".stripMargin)
  // }
  //
  //
  // (2 to 22).foreach(template)

  // format: off
  implicit class AsyncAdaptedAsPreparedStatement[In2, In, Out](private val fut: Future[AdaptedScalaPreparedStatement[In2, In, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[AdaptedScalaPreparedStatement[In2, In, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[AdaptedScalaPreparedStatement[In2, In, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: In2)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatementUnit[Out](private val fut: Future[ScalaPreparedStatementUnit[Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatementUnit[Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatementUnit[Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync()(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync())}

    def pager()(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager())

    def pager(pagingState: PagingState)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState).get)

    def pager[A: PagerSerializer](pagingState: A)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState).get)
  }

  implicit class AsyncAsPreparedStatement1[T1, Out](private val fut: Future[ScalaPreparedStatement1[T1, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement1[T1, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement1[T1, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatementMapped[T1, Out](private val fut: Future[ScalaPreparedStatementMapped[T1, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatementMapped[T1, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatementMapped[T1, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatement2[T1, T2, Out](private val fut: Future[ScalaPreparedStatement2[T1, T2, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement2[T1, T2, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement2[T1, T2, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2))}

    def pager(t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2))

    def pager(pagingState: PagingState, t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2).get)
  }

  implicit class AsyncAsPreparedStatement3[T1, T2, T3, Out](private val fut: Future[ScalaPreparedStatement3[T1, T2, T3, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement3[T1, T2, T3, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement3[T1, T2, T3, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3))}

    def pager(t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3).get)
  }

  implicit class AsyncAsPreparedStatement4[T1, T2, T3, T4, Out](private val fut: Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4).get)
  }

  implicit class AsyncAsPreparedStatement5[T1, T2, T3, T4, T5, Out](private val fut: Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5).get)
  }

  implicit class AsyncAsPreparedStatement6[T1, T2, T3, T4, T5, T6, Out](private val fut: Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6).get)
  }

  implicit class AsyncAsPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out](private val fut: Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7).get)
  }

  implicit class AsyncAsPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val fut: Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8).get)
  }

  implicit class AsyncAsPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val fut: Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9).get)
  }

  implicit class AsyncAsPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val fut: Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).get)
  }

  implicit class AsyncAsPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val fut: Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).get)
  }

  implicit class AsyncAsPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val fut: Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).get)
  }

  implicit class AsyncAsPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val fut: Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).get)
  }

  implicit class AsyncAsPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val fut: Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).get)
  }

  implicit class AsyncAsPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val fut: Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).get)
  }

  implicit class AsyncAsPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val fut: Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).get)
  }

  implicit class AsyncAsPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val fut: Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).get)
  }

  implicit class AsyncAsPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val fut: Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).get)
  }

  implicit class AsyncAsPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val fut: Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).get)
  }

  implicit class AsyncAsPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val fut: Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).get)
  }

  implicit class AsyncAsPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val fut: Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).get)
  }

  implicit class AsyncAsPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val fut: Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap {implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22).get)
  }

  implicit class AsyncApiPager[Out](private val pager: Future[ApiPager[Out]]) extends AnyVal {

    def executeAsync(pageSize: Int)(implicit session: CqlSession, ec: ExecutionContext): Future[(ApiPager[Out], Iterator[Out])] =
      pager.flatMap(_.executeAsync(pageSize))

  }
  // format: on
  // $COVERAGE-ON$
}
