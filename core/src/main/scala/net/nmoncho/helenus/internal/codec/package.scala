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

package net.nmoncho.helenus.internal

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.internal.core.`type`.codec.ParseUtils

package object codec {

  private[codec] val NULL = "NULL"

  /** Parses a value using a [[TypeCodec]] from a value, from a start position
    * @return value and next index to continue the parse process
    */
  def parseWithCodec[A](value: String, codec: TypeCodec[A], start: Int): (A, Int) = {
    val end = ParseUtils.skipCQLValue(value, start)

    codec.parse(value.substring(start, end)) -> end
  }

  /** Skips all spaces around an expected character (ie. spaces before and after)
    *
    * @param value value to check
    * @param index index to start checking
    * @param expected expected character
    * @return next index after trailing spaces
    */
  @throws[IllegalArgumentException]("if 'index' is EOF, or if 'value[index] != expected'")
  def skipSpacesAndExpect(value: String, index: Int, expected: Char): Int = {
    val idx = ParseUtils.skipSpaces(value, index)
    expectParseChar(value, idx, expected)
    ParseUtils.skipSpaces(value, idx + 1)
  }

  /** Expects a char at an index, for a given value
    */
  @throws[IllegalArgumentException]("if 'index' is EOF, or if 'value[index] != expected'")
  def expectParseChar(value: String, index: Int, expected: Char): Unit =
    if (index >= value.length) {
      throw new IllegalArgumentException(
        s"Cannot parse value from '$value', expecting '(', but got EOF"
      )
    } else if (value.charAt(index) != expected) {
      throw new IllegalArgumentException(
        s"Cannot parse value from '$value', at character $index expecting $expected but got '${value
            .charAt(index)}'"
      )
    }

  /** Checks if a parsing process is finished at the specified index, or if it should continue.
    *
    * @param value parsing value
    * @param index index to check
    * @param closingChar closing char for the given structure, in case the process should finish
    * @param separator element separator, in case the process should continue
    * @return true if finished, false if it should continue
    */
  @throws[IllegalArgumentException]("if 'index' is EOF, or if 'value[index] != separator'")
  def isParseFinished(value: String, index: Int, closingChar: Char, separator: Char): Boolean =
    if (index >= value.length) {
      throw new IllegalArgumentException(
        s"Malformed value '$value', missing closing '$closingChar', but got EOF"
      )
    } else if (value.charAt(index) == closingChar) {
      true
    } else if (value.charAt(index) != separator) {
      throw new IllegalArgumentException(
        s"Cannot parse collection value from '$value', at character $index expecting '$separator' but got '${value
            .charAt(index)}'"
      )
    } else {
      false
    }
}
