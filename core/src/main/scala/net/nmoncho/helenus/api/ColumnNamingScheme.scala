/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

import scala.collection.mutable

import com.datastax.oss.driver.internal.core.util.Strings

/** When mapping a case class to a Table or UDT,
  * a field can be mapped to column to a different format (e.g. `firstName` to `first_name`).
  *
  * A [[ColumnNamingScheme]] can be used for this purpose (inspired by Avro4s).
  * This trait assumes that the starting point is <b>camel case</b>
  */
sealed trait ColumnNamingScheme extends Serializable {
  def map(fieldName: String): String

  /** Returns the field name in a format appropriate for concatenation in a CQL query.
    *
    * @param pretty if `true`, use the shortest possible representation: if the identifier is
    *               case-insensitive, an unquoted, lower-case string, otherwise the double-quoted form. If
    *               `false`, always use the double-quoted form (this is slightly more efficient since we
    *               don't need to inspect the string).
    */
  def asCql(fieldName: String, pretty: Boolean): String = {
    val column = map(fieldName)

    if (pretty && !Strings.needsDoubleQuotes(column)) column
    else Strings.doubleQuote(column)
  }
}

object DefaultColumnNamingScheme extends ColumnNamingScheme {
  override def map(fieldName: String): String = fieldName
}

object SnakeCase extends ColumnNamingScheme {
  final val separator = '_'

  override def map(fieldName: String): String = {
    val col = mutable.ListBuffer[Char]()
    col += fieldName.head.toLower
    fieldName.tail.toCharArray.foreach { c =>
      if (c.isUpper) {
        col += separator
      }

      col += c.toLower
    }

    col.result().mkString
  }
}

object PascalCase extends ColumnNamingScheme {
  override def map(fieldName: String): String =
    if (fieldName.length == 1) fieldName.toUpperCase
    else {
      val chars = fieldName.toCharArray
      chars(0) = chars(0).toUpper
      new String(chars)
    }
}
