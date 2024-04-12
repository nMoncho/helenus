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

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.ScalaBoundStatement
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.cql.Mapping
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import org.slf4j.LoggerFactory
import shapeless.::
import shapeless.HList
import shapeless.HNil
import shapeless.LabelledGeneric
import shapeless.Lazy
import shapeless.Witness
import shapeless.labelled.FieldType
import shapeless.syntax.singleton.mkSingletonOps

// TODO check if ScalaPreparedStatement can be simplified
trait DerivedMapping[T] extends Mapping[T]

object DerivedMapping {

  private val log = LoggerFactory.getLogger(classOf[DerivedMapping[_]])

  type Binder[T, Out] = (ScalaBoundStatement[Out], T) => ScalaBoundStatement[Out]
  type FieldToColumn  = Map[String, String]

  trait Builder[T] extends (FieldToColumn => FieldCollector[T])

  def apply[T](renamed: Map[String, String])(
      implicit builder: Builder[T],
      classTag: ClassTag[T]
  ): Mapping[T] =
    new DefaultCaseClassDerivedMapping[T](builder(renamed), classTag, Map.empty)

  class DefaultCaseClassDerivedMapping[T](
      collector: FieldCollector[T],
      classTag: ClassTag[T],
      computedColumns: Map[String, BindParameterCollector[T]]
  ) extends DerivedMapping[T] {

    override def apply(row: Row): T = collector(row)

    override def apply(pstmt: PreparedStatement): T => BoundStatement =
      apply(pstmt.asInstanceOf[ScalaPreparedStatement[Unit, Row]])

    override def apply[Out](
        pstmt: ScalaPreparedStatement[T, Out]
    ): T => ScalaBoundStatement[Out] = {
      verify(pstmt)

      val collected = collector(pstmt)
      val requiredComputed = computedColumns.collect {
        case (_, computed) if computed.contains(pstmt) => computed(pstmt)
      }

      (t: T) =>
        requiredComputed.foldLeft(
          collected(pstmt.bind().asInstanceOf[ScalaBoundStatement[Out]], t)
        ) { (bstmt, compute) =>
          compute(bstmt, t)
        }
    }

    override def withComputedColumn[Col](column: String, compute: T => Col)(
        implicit codec: TypeCodec[Col]
    ): DerivedMapping[T] = {
      if (computedColumns.contains(column)) {
        log.warn("Column [{}] is already defined for Adapter and will be overridden", column)
      }

      new DefaultCaseClassDerivedMapping[T](
        collector,
        classTag,
        computedColumns + (column -> computedColumnCollector(column, compute, codec))
      )
    }

    private def verify(pstmt: PreparedStatement): Unit = {
      // Check if we have any missing parameters
      val expectedParameters = pstmt.getVariableDefinitions.asScala.map(_.getName.toString).toSet
      val usedParameters = computedColumns.foldLeft(collector.usedParameters(pstmt, Set.empty)) {
        case (params, (_, computed)) =>
          computed.usedParameters(pstmt, params)
      }

      if (expectedParameters != usedParameters) {
        log.error(
          "Invalid PreparedStatement [{}] expects [{}] bind parameters but only [{}] are available. Missing fields [{}] from class [{}]",
          pstmt.getQuery,
          expectedParameters.mkString(", "),
          usedParameters.mkString(", "),
          (expectedParameters -- usedParameters).mkString(", "),
          classTag.runtimeClass.getName
        )
      }

      // Check if we are overriding some fields with computed columns
      val redefined = collector.columns.intersect(computedColumns.keySet)
      if (redefined.nonEmpty) {
        log.warn(
          "Columns [{}] were redefined as computed columns and will override the values from the case class",
          redefined.mkString(", ")
        )
      }
    }
  }

  /** A [[BindParameterCollector]] allows to bind a parameter [[T]] to a [[PreparedStatement]]
    *
    * @tparam T type to bind to the [[PreparedStatement]]
    */
  sealed trait BindParameterCollector[T] {

    /** Produces a [[Binder]] function that will bind the parameter if it's defined in
      * the [[PreparedStatement]] or just return the same [[BoundStatement]]
      *
      * @param pstmt to bind
      * @return binder function
      */
    def apply[Out](pstmt: ScalaPreparedStatement[T, Out]): Binder[T, Out]

    /** Checks if the [[PreparedStatement]] uses the [[column]] defined by this [[BindParameterCollector]], and
      * accumulates the results in an accumulator
      *
      * @param pstmt statement to inspect
      * @param accumulated accumulated used columns so far
      * @return accumulated columns, plus this [[column]] if the [[PreparedStatement]] uses it
      */
    def usedParameters(pstmt: PreparedStatement, accumulated: Set[String]): Set[String]

    protected def column: String

    def columns: Set[String]

    /** Checks if the [[PreparedStatement]] uses the [[column]] defined by this [[BindParameterCollector]]
      *
      * Unlike [[usedParameters]], this doesn't accumulate used columns, it just checks this one.
      *
      * @param pstmt statement to inspect
      * @return
      */
    def contains(pstmt: PreparedStatement): Boolean =
      pstmt.getVariableDefinitions.contains(column)
  }

  /** A [[FieldCollector]] is a [[BindParameterCollector]] that comes from a case class field,
    * unlike a [[computedColumnCollector]].
    *
    * This field can be used to derive a [[RowMapper]]
    *
    * @tparam T type to bind to the [[PreparedStatement]]
    */
  sealed trait FieldCollector[T] extends BindParameterCollector[T] {
    def apply(row: Row): T
  }

  /** Creates [[BindParameterCollector]] for a computed column.
    *
    * Computed columns will not be considered when mapping a case class with a [[RowMapper]]. If that's desired
    * then the column should be promoted to a case class field.
    *
    * @param columnName name of the computed column
    * @param compute how to compute a value
    * @param codec how to encode the computed column value
    * @tparam T where does the value come from
    * @tparam Col computed column type
    * @return a <em>flat</em> [[BindParameterCollector]] for a computed column
    */
  private def computedColumnCollector[T, Col](
      columnName: String,
      compute: T => Col,
      codec: TypeCodec[Col]
  ): BindParameterCollector[T] = new BindParameterCollector[T] {
    override protected val column: String = columnName

    override val columns: Set[String] = Set(column)

    override def usedParameters(pstmt: PreparedStatement, accumulated: Set[String]): Set[String] =
      if (contains(pstmt)) {
        verifyColumnType(pstmt, column, codec)

        accumulated + column
      } else {
        accumulated
      }

    override def apply[Out](pstmt: ScalaPreparedStatement[T, Out]): Binder[T, Out] =
      if (contains(pstmt) && pstmt.options.ignoreNullFields) { (bstmt, t) =>
        setIfDefined(bstmt, column, compute(t), codec)
      } else if (contains(pstmt)) { (bstmt, t) =>
        bstmt.set(column, compute(t), codec).asInstanceOf[ScalaBoundStatement[Out]]
      } else { (bstmt, _) =>
        log.debug("Ignoring missing column [{}] from query [{}]", column, pstmt.getQuery: Any)
        bstmt
      }
  }

  implicit def lastElementCollector[K <: Symbol, H](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): Builder[FieldType[K, H] :: HNil] = (mapping: FieldToColumn) =>
    new FieldCollector[FieldType[K, H] :: HNil] {

      override protected val column: String =
        mapping.getOrElse(witness.value.name, columnMapper.map(witness.value.name))

      override val columns: Set[String] = Set(column)

      override def usedParameters(
          pstmt: PreparedStatement,
          accumulated: Set[String]
      ): Set[String] =
        if (contains(pstmt)) {
          verifyColumnType(pstmt, column, codec)

          accumulated + column
        } else {
          accumulated
        }

      override def apply(row: Row): FieldType[K, H] :: HNil =
        (witness.value ->> row.get(column, codec)).asInstanceOf[FieldType[K, H]] :: HNil

      override def apply[Out](
          pstmt: ScalaPreparedStatement[FieldType[K, H] :: HNil, Out]
      ): Binder[FieldType[K, H] :: HNil, Out] =
        if (contains(pstmt) && pstmt.options.ignoreNullFields) { (bstmt, t) =>
          setIfDefined(bstmt, column, t.head, codec)
        } else if (contains(pstmt)) { (bstmt, t) =>
          bstmt.set(column, t.head, codec).asInstanceOf[ScalaBoundStatement[Out]]
        } else { (bstmt, _) =>
          log.debug("Ignoring missing column [{}] from query [{}]", column, pstmt.getQuery: Any)
          bstmt
        }
    }

  implicit def hListCollector[K <: Symbol, H, T <: HList](
      implicit codec: TypeCodec[H],
      witness: Witness.Aux[K],
      tailBuilder: Builder[T],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): Builder[FieldType[K, H] :: T] = (mapping: FieldToColumn) =>
    new FieldCollector[FieldType[K, H] :: T] {

      override protected val column: String =
        mapping.getOrElse(witness.value.name, columnMapper.map(witness.value.name))

      private val tailCollector: FieldCollector[T] with BindParameterCollector[T] = tailBuilder(
        mapping
      )

      override val columns: Set[String] = tailCollector.columns + column

      override def usedParameters(
          pstmt: PreparedStatement,
          accumulated: Set[String]
      ): Set[String] =
        if (contains(pstmt)) {
          verifyColumnType(pstmt, column, codec)

          tailCollector.usedParameters(pstmt, accumulated + column)
        } else {
          tailCollector.usedParameters(pstmt, accumulated)
        }

      override def apply(row: Row): FieldType[K, H] :: T =
        (witness.value ->> row.get(column, codec))
          .asInstanceOf[FieldType[K, H]] :: tailCollector(row)

      override def apply[Out](
          pstmt: ScalaPreparedStatement[FieldType[K, H] :: T, Out]
      ): Binder[FieldType[K, H] :: T, Out] = {
        val tail = tailCollector(pstmt.asInstanceOf[ScalaPreparedStatement[T, Out]])

        // TODO investigate if we can do a short-circuit to avoid processing fields after all have been bound
        if (contains(pstmt) && pstmt.options.ignoreNullFields) { (bstmt, t) =>
          val bs = setIfDefined(bstmt, column, t.head, codec)

          tail(bs, t.tail)
        } else if (contains(pstmt)) { (bstmt, t) =>
          val bs = bstmt.set(column, t.head, codec).asInstanceOf[ScalaBoundStatement[Out]]

          tail(bs, t.tail)
        } else { (bstmt, t) =>
          log.debug("Ignoring missing column [{}] from query [{}]", column, pstmt.getQuery: Any)
          tail(bstmt, t.tail)
        }
      }

    }

  /** [[BindParameterCollector]] for [[LabelledGeneric]] instance
    *
    * Maps a [[LabelledGeneric]] instance to/from an instance of [[A]]
    *
    * @return a [[Builder]] for a case class of type [[A]]
    */
  implicit def genericCollector[A, R](
      implicit gen: LabelledGeneric.Aux[A, R],
      collectorBuilder: Lazy[Builder[R]],
      columnMapper: ColumnNamingScheme = DefaultColumnNamingScheme
  ): Builder[A] = (mappings: FieldToColumn) => {
    val collector = collectorBuilder.value(mappings)

    new FieldCollector[A] {

      override protected val column: String = "<not-used>"

      override val columns: Set[String] = collector.columns

      override def apply(row: Row): A = gen.from(collector(row))

      override def apply[Out](pstmt: ScalaPreparedStatement[A, Out]): Binder[A, Out] = {
        val binder = collector[Out](pstmt.asInstanceOf[ScalaPreparedStatement[R, Out]])

        (bstmt, a) => binder(bstmt, gen.to(a))
      }

      override def usedParameters(pstmt: PreparedStatement, accumulated: Set[String]): Set[String] =
        collector.usedParameters(pstmt, accumulated)

    }
  }

  /** Sets the value to the [[BoundStatement]] only if not-null to avoid tombstones
    */
  private def setIfDefined[T, Out](
      bstmt: ScalaBoundStatement[Out],
      column: String,
      value: T,
      codec: TypeCodec[T]
  ): ScalaBoundStatement[Out] =
    if (value == null || value == None) bstmt
    else bstmt.set(column, value, codec).asInstanceOf[ScalaBoundStatement[Out]]

  /** Verifies if a [[TypeCodec]] can <em>accept</em> (or handle) a column defined in a [[PreparedStatement]]
    *
    * Logs a warning if it can't
    */
  private def verifyColumnType(
      pstmt: PreparedStatement,
      column: String,
      codec: TypeCodec[_]
  ): Unit = {
    val columnDefinition = pstmt.getVariableDefinitions.get(column)
    if (!codec.accepts(columnDefinition.getType)) {
      log.warn(
        "Invalid PreparedStatement expected parameter with type {} for name {} but got type {}",
        columnDefinition.getType.toString,
        column,
        codec.getCqlType.toString
      )
    }
  }
}
