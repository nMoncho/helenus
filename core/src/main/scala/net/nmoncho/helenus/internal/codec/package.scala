/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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

  /** Skips spaces and expects a CQL identifier
    *
    * @param value value to parse
    * @param index start index to consider
    * @param expectedId expected id
    * @return index from where to continue
    */
  @throws[IllegalArgumentException]("if 'index' is EOF, or if 'value != expected'")
  def skipSpacesAndExpectId(value: String, index: Int, expectedId: String): Int = {
    val idx = ParseUtils.skipSpaces(value, index)
    val end = expectParseId(value, idx, expectedId)
    ParseUtils.skipSpaces(value, end)
  }

  @throws[IllegalArgumentException]("if 'index' is EOF, or if 'value != expected'")
  private def expectParseId(value: String, start: Int, expectedId: String): Int =
    if (start >= value.length) {
      throw new IllegalArgumentException(
        s"Cannot parse value from '$value', expecting '$expectedId', but got EOF"
      )
    } else {
      val end    = ParseUtils.skipCQLId(value, start)
      val parsed = value.substring(start, end)
      if (parsed != expectedId) {
        throw new IllegalArgumentException(
          s"Cannot parse value from '$value', expecting '$expectedId' but got '$parsed'"
        )
      }

      end
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
        s"Cannot parse value from '$value', expecting '$expected', but got EOF"
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
