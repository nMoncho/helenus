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
