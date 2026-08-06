/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho

import scala.annotation.unused
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.experimental.macros
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.MappedAsyncPagingIterable
import com.datastax.oss.driver.api.core.PagingIterable
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.registry.MutableCodecRegistry
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile
import com.datastax.oss.driver.api.core.cql._
import com.datastax.oss.driver.api.core.data.SettableByIndex
import com.datastax.oss.driver.api.core.data.SettableByName
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata
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

  private val log = LoggerFactory.getLogger("net.nmoncho.helenus")

  /** Lifts an implicit synchronous [[CqlSession]] into an implicit
    * `Future[CqlSession]`, so the async execution extension methods (which take a
    * `Future[CqlSession]`) can be used when only a synchronous session is in scope.
    *
    * <b>Note:</b> the lift is [[Future.successful]]; it does <em>not</em> move work
    * off the calling thread. The session is wrapped as an already-completed future,
    * so the subsequent `map`/`flatMap` on it may run on the calling thread depending
    * on the [[ExecutionContext]]. Because this is always in scope via
    * `import net.nmoncho.helenus._`, an async execution can be selected from a plain
    * `CqlSession` without an explicit `Future[CqlSession]` — keep that in mind when
    * reasoning about which thread the driver call runs on.
    */
  implicit def cqlSessionAdapter(implicit session: CqlSession): Future[CqlSession] =
    Future.successful(session)

  val Adapter = net.nmoncho.helenus.api.cql.Adapter
  type Adapter[In, Out] = net.nmoncho.helenus.api.cql.Adapter[In, Out]

  val ColumnNamingScheme = net.nmoncho.helenus.api.ColumnNamingScheme
  type ColumnNamingScheme = net.nmoncho.helenus.api.ColumnNamingScheme

  val ColumnMapper = net.nmoncho.helenus.api.RowMapper.ColumnMapper
  type ColumnMapper[T] = net.nmoncho.helenus.api.RowMapper.ColumnMapper[T]

  val Mapping = net.nmoncho.helenus.api.cql.Mapping
  type Mapping[T] = net.nmoncho.helenus.api.cql.Mapping[T]

  val Pager = net.nmoncho.helenus.internal.cql.Pager
  type Pager[T] = net.nmoncho.helenus.api.cql.Pager[T]

  val PagerSerializer = net.nmoncho.helenus.api.cql.PagerSerializer
  type PagerSerializer[In] = net.nmoncho.helenus.api.cql.PagerSerializer[In]

  val RowMapper = net.nmoncho.helenus.api.RowMapper
  type RowMapper[Out] = net.nmoncho.helenus.api.RowMapper[Out]

  val ScalaPreparedStatement = net.nmoncho.helenus.api.cql.ScalaPreparedStatement
  type ScalaPreparedStatement[In, Out] = net.nmoncho.helenus.api.cql.ScalaPreparedStatement[In, Out]

  val StatementOptions = net.nmoncho.helenus.api.cql.StatementOptions
  type StatementOptions = net.nmoncho.helenus.api.cql.StatementOptions

  val ScalaBoundStatement   = net.nmoncho.helenus.api.cql.ScalaBoundStatement
  val WrappedBoundStatement = net.nmoncho.helenus.api.cql.WrappedBoundStatement
  type ScalaBoundStatement[Out] = net.nmoncho.helenus.api.cql.ScalaBoundStatement[Out]

  implicit val defaultIdentityRowMapper: RowMapper[Row] = RowMapper.identity

  implicit class CqlSessionOps(private val session: CqlSession) extends AnyVal {

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
              case codec: UDTCodec[_]
                  if codec.isKeyspaceBlank || !codec.existsInKeyspace(session) =>
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
    * There is also an asynchronous alternative, which is `cqlAsync` instead of `cql`.
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

  implicit class SettableByIndexOps[Self <: SettableByIndex[Self]](private val bs: Self)
      extends AnyVal {

    /** Sets or binds the specified value only if it's not NULL, avoiding a tombstone insert.
      *
      * @param index position of bound parameter
      * @param value value to be bound
      * @param codec how to encode the provided value
      * @tparam T
      * @return a modified version of this [[BoundStatement]]
      */
    @inline private[helenus] def setIdxIfDefined[T](
        index: Int,
        value: T,
        codec: TypeCodec[T]
    ): Self =
      if (value == null || value == None) bs else bs.set(index, value, codec)
  }

  implicit class SettableByNameOps[Self <: SettableByName[Self]](private val bs: Self)
      extends AnyVal {

    /** Sets or binds the specified value only if it's not NULL, avoiding a tombstone insert.
      *
      * @param index position of bound parameter
      * @param value value to be bound
      * @param codec how to encode the provided value
      * @tparam T
      * @return a modified version of this [[BoundStatement]]
      */
    @inline private[helenus] def setNameIfDefined[T](
        name: String,
        value: T,
        codec: TypeCodec[T]
    ): Self =
      if (value == null || value == None) bs else bs.set(name, value, codec)
  }

  /** Extension methods for [[Future]] of [[BoundStatement]], helping you execute them with the proper context.
    */
  // $COVERAGE-OFF$
  implicit class BoundStatementAsyncOps[Out](private val bstmt: Future[ScalaBoundStatement[Out]])
      extends AnyVal {

    /** Executes this CQL Statement, returning the results in a [[Future]].
      *
      * This uses the driver's blocking `execute` under the hood, run inside the
      * provided [[ExecutionContext]]; the returned [[Future]] completes once that
      * (blocking) call returns. For a fully non-blocking execution use [[executeAsync]].
      */
    def execute()(
        implicit session: Future[CqlSession],
        ec: ExecutionContext
    ): Future[PagingIterable[Out]] =
      session.flatMap { implicit s: CqlSession =>
        bstmt.map(_.execute())
      }

    /** Executes this CQL Statement asynchronously
      */
    def executeAsync()(
        implicit session: Future[CqlSession],
        ec: ExecutionContext
    ): Future[MappedAsyncPagingIterable[Out]] =
      session.flatMap { implicit s: CqlSession =>
        bstmt.flatMap(_.executeAsync())
      }

    /** Returns a [[Publisher]] that, once subscribed to, executes the given query and emits all
      * the results.
      */
    def executeReactive()(
        implicit session: Future[CqlSession],
        ec: ExecutionContext
    ): Future[Publisher[Out]] =
      session.flatMap { implicit s: CqlSession =>
        bstmt.map(_.executeReactive())
      }

    /** Creates an initial [[Pager]] for this CQL statement
      */
    def pager(implicit ec: ExecutionContext): Future[Pager[Out]] =
      bstmt.map(_.pager)

    /** Creates an continued [[Pager]] for this CQL statement from a [[PagingState]]
      */
    def pager(
        pagingState: PagingState
    )(implicit ec: ExecutionContext): Future[Pager[Out]] =
      bstmt.flatMap(_.pager(pagingState) match {
        case Success(value) => Future.successful(value)
        case Failure(exception) => Future.failed(exception)
      })

    /** Creates an continued [[Pager]] for this CQL statement from a [[PagingState]]
      */
    def pager[A: PagerSerializer](pagingState: A)(
        implicit ec: ExecutionContext
    ): Future[Pager[Out]] =
      bstmt.flatMap(_.pager[A](pagingState) match {
        case Success(value) => Future.successful(value)
        case Failure(exception) => Future.failed(exception)
      })

    /** Set options to this [[BoundStatement]] while returning the original type
      */
    def withOptions(fn: BoundStatement => BoundStatement)(
        implicit ec: ExecutionContext
    ): Future[ScalaBoundStatement[Out]] =
      bstmt.map(b => fn(b).asInstanceOf[ScalaBoundStatement[Out]])

    /** Set options to this [[BoundStatement]] while returning the original type
      */
    def withOptions(options: StatementOptions)(
        implicit ec: ExecutionContext
    ): Future[ScalaBoundStatement[Out]] =
      bstmt.map(b => options(b).asInstanceOf[ScalaBoundStatement[Out]])
  }
  // $COVERAGE-ON$

  implicit class PreparedStatementSyncStringOps(private val query: String) extends AnyVal {

    def toCQL(implicit session: CqlSession): CQLQuery =
      macro internal.macros.CqlQueryInterpolation.toCQL

    def toCQLAsync(
        implicit futSession: Future[CqlSession],
        ec: ExecutionContext
    ): Future[CQLQuery] =
      macro internal.macros.CqlQueryInterpolation.toCQLAsync

    def toUnsafeCQL(implicit session: CqlSession): CQLQuery =
      CQLQuery(query, session)

    def toUnsafeCQLAsync(
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
        implicit @unused cbf: BuildFrom[Nothing, T, Col[T]]
    ): Col[T] = iter.to(factory)
  }

  implicit class MappedAsyncPagingIterableOps[T](private val pi: MappedAsyncPagingIterable[T])
      extends AnyVal {
    import net.nmoncho.helenus.internal.compat.FutureConverters._

    import scala.jdk.CollectionConverters._

    /** Current page as a Scala [[Iterator]]
      */
    def currPage: Iterator[T] = pi.currentPage().iterator().asScala

    /** Returns the next element from the results.
      *
      * Use this method when you <em>only<em> care about the first element.
      *
      * Unlike [[nextOption]], this method doesn't expose the mutated [[MappedAsyncPagingIterable]],
      * so it's not meant for iterating through all results.
      *
      * @return [[Some]] value if results haven't been exhausted, [[None]] otherwise
      */
    def oneOption: Option[T] = Option(pi.one())

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
      *
      * @param timeout how much time to wait for the next page to be ready
      * @param ec
      */
    def iter(timeout: FiniteDuration)(implicit @unused ec: ExecutionContext): Iterator[T] =
      // A hand-rolled Iterator that fetches the next page (blocking) only once the
      // current page is exhausted -- lazily and identically on Scala 2.12 and 2.13.
      // The previous `scala.collection.compat` `concat` was by-name (lazy) on 2.13
      // but strict on 2.12, which forced every page to be fetched up front.
      new Iterator[T] {
        private var current: MappedAsyncPagingIterable[T] = pi
        private var page: java.util.Iterator[T]           = pi.currentPage().iterator()

        override def hasNext: Boolean = {
          while (!page.hasNext && current.hasMorePages) {
            log.debug("fetching more pages")
            current = Await.result(current.fetchNextPage().asScala, timeout)
            page    = current.currentPage().iterator()
          }
          page.hasNext
        }

        override def next(): T =
          if (hasNext) page.next()
          else Iterator.empty.next()
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

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[AdaptedScalaPreparedStatement[In2, In, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: In2)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: In2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatementUnit[Out](private val fut: Future[ScalaPreparedStatementUnit[Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatementUnit[Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatementUnit[Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatementUnit[Out]] = fut.map(_.withOptions(opts))

    def executeAsync()(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync())}

    def pager()(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager())

    def pager(pagingState: PagingState)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState).get)

    def pager[A: PagerSerializer](pagingState: A)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState).get)
  }

  implicit class AsyncAsPreparedStatement1[T1, Out](private val fut: Future[ScalaPreparedStatement1[T1, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement1[T1, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement1[T1, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement1[T1, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatementMapped[T1, Out](private val fut: Future[ScalaPreparedStatementMapped[T1, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatementMapped[T1, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatementMapped[T1, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatementMapped[T1, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1))}

    def pager(t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1))

    def pager(pagingState: PagingState, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1).get)
  }

  implicit class AsyncAsPreparedStatement2[T1, T2, Out](private val fut: Future[ScalaPreparedStatement2[T1, T2, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement2[T1, T2, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement2[T1, T2, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement2[T1, T2, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2))}

    def pager(t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2))

    def pager(pagingState: PagingState, t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2).get)
  }

  implicit class AsyncAsPreparedStatement3[T1, T2, T3, Out](private val fut: Future[ScalaPreparedStatement3[T1, T2, T3, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement3[T1, T2, T3, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement3[T1, T2, T3, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement3[T1, T2, T3, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3))}

    def pager(t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3).get)
  }

  implicit class AsyncAsPreparedStatement4[T1, T2, T3, T4, Out](private val fut: Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement4[T1, T2, T3, T4, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4).get)
  }

  implicit class AsyncAsPreparedStatement5[T1, T2, T3, T4, T5, Out](private val fut: Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement5[T1, T2, T3, T4, T5, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5).get)
  }

  implicit class AsyncAsPreparedStatement6[T1, T2, T3, T4, T5, T6, Out](private val fut: Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6).get)
  }

  implicit class AsyncAsPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out](private val fut: Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7).get)
  }

  implicit class AsyncAsPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out](private val fut: Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8).get)
  }

  implicit class AsyncAsPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out](private val fut: Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9).get)
  }

  implicit class AsyncAsPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out](private val fut: Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10).get)
  }

  implicit class AsyncAsPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out](private val fut: Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11).get)
  }

  implicit class AsyncAsPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out](private val fut: Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12).get)
  }

  implicit class AsyncAsPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out](private val fut: Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13).get)
  }

  implicit class AsyncAsPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out](private val fut: Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14).get)
  }

  implicit class AsyncAsPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out](private val fut: Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15).get)
  }

  implicit class AsyncAsPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out](private val fut: Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16).get)
  }

  implicit class AsyncAsPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out](private val fut: Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17).get)
  }

  implicit class AsyncAsPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out](private val fut: Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18).get)
  }

  implicit class AsyncAsPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out](private val fut: Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19).get)
  }

  implicit class AsyncAsPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out](private val fut: Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20).get)
  }

  implicit class AsyncAsPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out](private val fut: Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))}

    def pager(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21))

    def pager(pagingState: PagingState, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).get)

    def pager[A: PagerSerializer](pagingState: A, t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21)(implicit ec: ExecutionContext): Future[ApiPager[Out]] = fut.map(_.pager(pagingState, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21).get)
  }

  implicit class AsyncAsPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out](private val fut: Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]]) extends AnyVal {
    def as[Out2](implicit ec: ExecutionContext, mapper: RowMapper[Out2], ev: Out =:= Row): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out2]] = fut.map(_.as[Out2])

    def as[Out2](mapper: RowMapper[Out2])(implicit ec: ExecutionContext, ev: Out =:= Row): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out2]] = fut.map(_.as[Out2](ev, mapper))

    def withOptions(opts: StatementOptions)(implicit ec: ExecutionContext): Future[ScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Out]] = fut.map(_.withOptions(opts))

    def executeAsync(t1: T1, t2: T2, t3: T3, t4: T4, t5: T5, t6: T6, t7: T7, t8: T8, t9: T9, t10: T10, t11: T11, t12: T12, t13: T13, t14: T14, t15: T15, t16: T16, t17: T17, t18: T18, t19: T19, t20: T20, t21: T21, t22: T22)(implicit cqlSession: Future[CqlSession], ec: ExecutionContext): Future[MappedAsyncPagingIterable[Out]] = cqlSession.flatMap{implicit s => fut.flatMap(_.executeAsync(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22))}

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
