/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

import java.util.concurrent.ConcurrentHashMap

import scala.annotation.unused
import scala.reflect.ClassTag
import scala.util.Try

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.internal.DerivedRowMapper
import net.nmoncho.helenus.internal.macros.{ RowMapper => RowMapperMacros }
import org.slf4j.LoggerFactory
import shapeless.<:!<
import shapeless.IsTuple
import shapeless.Lazy

/** Maps a [[Row]] into a [[T]]
  *
  * @tparam T type to get from a row
  */
trait RowMapper[T] extends Serializable {

  def apply(row: Row): T

}

object RowMapper {
  import scala.language.experimental.macros

  type ColumnName = String

  val identity: RowMapper[Row] = (row: Row) => row

  trait SafeRowMapper[T] extends RowMapper[Try[T]]

  object SafeRowMapper {

    def apply[T](implicit mapper: SafeRowMapper[T]): SafeRowMapper[T] =
      mapper

    implicit def fromUnsafe[T](implicit mapper: RowMapper[T]): SafeRowMapper[T] =
      (row: Row) => Try(mapper(row))

  }

  /** Knows how to extract a column from a [[Row]] into a Scala type [[A]]
    * @tparam A target type
    */
  trait ColumnMapper[A] extends Serializable {
    def apply(columnName: ColumnName, row: Row): A
  }

  object ColumnMapper {
    private val log = LoggerFactory.getLogger(classOf[ColumnMapper[_]])

    def default[A](implicit codec: TypeCodec[A]): ColumnMapper[A] = new ColumnMapper[A] {
      override def apply(columnName: ColumnName, row: Row): A =
        row.get(columnName, codec)
    }

    /** Creates a [[ColumnMapper]] that maps an [[Either]] to different columns
      *
      * @param leftColumnName column name where [[Left]] is stored
      * @param rightColumnName column name where [[Right]] is stored
      * @param leftCodec codec for [[Left]] value
      * @param rightCodec codec for [[Right]] value
      * @tparam A [[Left]] type
      * @tparam B [[Right]] type
      * @return [[ColumnMapper]] for an [[Either]]
      */
    def either[A, B](leftColumnName: String, rightColumnName: String)(
        implicit leftCodec: TypeCodec[A],
        rightCodec: TypeCodec[B]
    ): ColumnMapper[Either[A, B]] = new ColumnMapper[Either[A, B]] {
      override def apply(ignored: ColumnName, row: Row): Either[A, B] =
        if (row.isNull(leftColumnName) && !row.isNull(rightColumnName)) {
          Right(row.get[B](rightColumnName, rightCodec))
        } else if (!row.isNull(leftColumnName) && row.isNull(rightColumnName)) {
          Left(row.get[A](leftColumnName, leftCodec))
        } else {
          log.warn(
            "Both columns [{}] and [{}] where not null, defaulting to Right",
            leftColumnName,
            rightColumnName: Any
          )
          Right(row.get[B](rightColumnName, rightCodec))
        }
    }

    /** Creates a [[ColumnMapper]] that explodes each field of the case class
      * into a column. This is in contrast of having an UDT where the case class
      * is mapped to a single column.
      *
      * @param prefix column name prefix to apply to every non-renamed field
      * @param renamedFields field renamed to columns. Doesn't use `prefix`
      * @tparam T case class type
      * @return column mapper
      */
    def of[T](prefix: String, renamedFields: T => (Any, ColumnName)*): ColumnMapper[T] =
      macro RowMapperMacros.derivedColumnMapper[DerivedRowMapper.Builder, T]

  }

  /** Derives a [[RowMapper]] considering the specified name mapping.
    *
    * @param renamedFields renamed fields
    * @tparam T target type
    */
  def apply[T](renamedFields: T => (Any, ColumnName)*): RowMapper[T] =
    macro RowMapperMacros.renamedMapper[DerivedRowMapper.Builder, T]

  /** Auto-derives a [[RowMapper]] for [[T]].
    *
    * '''Performance note:''' derivation builds a nested mapper structure and computes the
    * field-to-column name transforms once, when this method is materialized. Because the
    * `row.as[T]` / `resultSet.as[T]` extension methods take the [[RowMapper]] as an ''implicit''
    * parameter, calling them in a hot loop without a bound mapper re-runs this derivation on
    * every iteration. Bind the mapper to a single `implicit val` (or use [[cached]]) so the
    * derivation happens once and is reused:
    *
    * {{{
    * // Derived once, reused for every row:
    * implicit val fooMapper: RowMapper[Foo] = RowMapper.of[Foo]
    * rows.map(_.as[Foo])
    * }}}
    */
  def of[T](implicit mapper: DerivedRowMapper[T]): RowMapper[T] = mapper

  /** Process-wide cache of auto-derived mappers, keyed by target class and naming scheme.
    *
    * Keying on the [[ColumnNamingScheme]] keeps the cache correct when the same case class is
    * mapped under different schemes; the [[ColumnNamingScheme]] hierarchy is `sealed` and its
    * members are stable singletons, so reference-based keys are safe.
    */
  private val derivedCache =
    new ConcurrentHashMap[(Class[_], ColumnNamingScheme), RowMapper[_]]()

  /** Auto-derives a [[RowMapper]] for [[T]] once and caches it for the lifetime of the process.
    *
    * Behaves like [[of]] but memoizes the derived mapper, keyed by [[T]]'s runtime class and the
    * in-scope [[ColumnNamingScheme]]. Repeated calls (including an accidental call inside a hot
    * loop) return the same instance and never re-run the derivation. The [[Lazy]] wrapper ensures
    * the (potentially expensive) derivation is only forced on a cache miss.
    *
    * This is intended for the common case where a given type is always mapped with the same codecs
    * and naming scheme. If you need per-call-site control over the codecs used, bind a mapper
    * explicitly with `implicit val ... = RowMapper.of[T]` instead.
    *
    * {{{
    * implicit val fooMapper: RowMapper[Foo] = RowMapper.cached[Foo]
    * }}}
    */
  def cached[T]()(
      implicit mapper: Lazy[DerivedRowMapper[T]],
      tag: ClassTag[T],
      naming: ColumnNamingScheme = ColumnNamingScheme.Default
  ): RowMapper[T] =
    // TODO add the ability to rename fields on a cached instance
    derivedCache
      .computeIfAbsent((tag.runtimeClass, naming), _ => mapper.value)
      .asInstanceOf[RowMapper[T]]

  /** Derives a [[RowMapper]] for tuples
    */
  implicit def derivedTupleRowMapper[T](
      implicit @unused ev: IsTuple[T],
      mapper: DerivedRowMapper[T]
  ): RowMapper[T] = mapper

  /** Derives a [[RowMapper]] from a [[TypeCodec]] when [[T]] isn't a `Product`
    */
  implicit def simpleRowMapper[T](
      implicit ev: T <:!< Product,
      codec: TypeCodec[T]
  ): DerivedRowMapper[T] =
    (row: Row) => row.get(0, codec)

}
