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

package net.nmoncho.helenus.api.`type`.codec

import scala.collection.mutable

/** When mapping a case class to a Table or UDT,
  * a field can be mapped to column to a different format (e.g. `firstName` to `first_name`).
  *
  * A [[ColumnMapper]] can be used for this purpose (inspired by Avro4s).
  * This trait assumes that the starting point is <b>camel case</b>
  */
sealed trait ColumnMapper {
  def map(column: String): String
}

object DefaultColumnMapper extends ColumnMapper {
  override def map(column: String): String = column
}

object SnakeCase extends ColumnMapper {
  final val separator = '_'

  override def map(column: String): String = {
    val col = mutable.ListBuffer[Char]()
    col += column.head.toLower
    column.tail.toCharArray.foreach { c =>
      if (c.isUpper) {
        col += separator
      }

      col += c.toLower
    }

    col.result().mkString
  }
}

object PascalCase extends ColumnMapper {
  override def map(column: String): String =
    if (column.length == 1) column.toUpperCase
    else {
      val chars = column.toCharArray
      chars(0) = chars(0).toUpper
      new String(chars)
    }
}
