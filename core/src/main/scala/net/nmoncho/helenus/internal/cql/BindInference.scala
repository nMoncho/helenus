/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import scala.annotation.tailrec

import com.datastax.oss.driver.internal.core.util.Strings

/** Bind-versus-inject inference for the `cql"..."` interpolator.
  *
  * The interpolator has to decide, for each interpolated parameter, whether it is a '''value''' to
  * bind (a `:name` marker the driver fills in) or a fragment to '''inject''' into the query text (a
  * table or column name, which cannot be a bind marker). This is pure, side-effect-free logic driven
  * entirely by [[CqlValidator]], extracted from the macro so it can be unit- and property-tested
  * directly rather than only through end-to-end interpolation.
  */
private[helenus] object BindInference {

  /** Decides which parameters can be bound, by asking the grammar where a bind marker fits.
    *
    * Every parameter starts out as a bind marker and the statement is handed to the parser as a
    * whole. Whenever the parser rejects one, that parameter is injected into the query text
    * instead and the statement is checked again, until the parser is happy. A parameter the
    * parser rejects and the macro can't inject - anything that isn't a compile-time constant -
    * is left alone, for the caller's validation to report.
    *
    * Each round settles one parameter for good, so this converges in at most one round per
    * parameter.
    *
    * @param parts String Interpolated constant parts
    * @param names bind marker name of each parameter
    * @param injectable query text of each parameter that can be injected as is
    * @return whether each parameter is to be bound
    */
  def boundParams(
      parts: Seq[String],
      names: Seq[String],
      injectable: Seq[Option[String]]
  ): Seq[Boolean] = {
    val bound = Array.fill(names.size)(true)

    def statement: String = interleave(parts, tokensOf(names, injectable, bound))

    // Where a parameter's text starts within the statement built out of the current decisions
    def startOf(idx: Int): Int =
      parts.take(idx + 1).map(_.length).sum +
        tokensOf(names, injectable, bound).take(idx).map(_.length).sum

    // A bind marker the parser accepted, but which isn't a marker at all: the caller wrapped it
    // in quotes, so it ended up as part of a string literal rather than as a token of its own
    def swallowed(stmt: String): Option[Int] = {
      val markers = CqlValidator.bindMarkerOffsets(stmt)

      names.indices.find(idx => bound(idx) && injectable(idx).isDefined && !markers(startOf(idx)))
    }

    // The rightmost parameter the parser could be rejecting: one that starts no later than the
    // offending token, and that can still be injected instead. Anything further to the right
    // can't be the cause, since parsing stops at the first error.
    def rejected(stmt: String): Option[Int] =
      CqlValidator.firstErrorOffset(stmt).flatMap { offset =>
        names.indices.reverse
          .find(idx => bound(idx) && injectable(idx).isDefined && startOf(idx) <= offset)
      }

    @tailrec
    def settle(rounds: Int): Unit =
      if (rounds > 0) {
        val stmt = statement

        rejected(stmt).orElse(swallowed(stmt)) match {
          case Some(idx) =>
            bound(idx) = false
            settle(rounds - 1)

          // Either the statement is valid, or nothing else can be injected
          case None => ()
        }
      }

    settle(names.size)

    // A statement that can't be made valid is reported with every constant injected as the user
    // wrote it, so that the error points at the query they typed rather than at bind markers
    // they never asked for
    if (CqlValidator.firstErrorOffset(statement).isEmpty) bound.toSeq
    else injectable.map(_.isEmpty)
  }

  /** Text each parameter contributes to the query: a bind marker, or the constant it folds to */
  def tokensOf(
      names: Seq[String],
      injectable: Seq[Option[String]],
      bound: Seq[Boolean]
  ): Seq[String] =
    names.indices.map { idx =>
      // Only a parameter that can be injected is ever left unbound, so the fallback below is
      // there to keep this total rather than because it can be reached
      if (bound(idx)) bindMarker(names(idx))
      else injectable(idx).getOrElse(bindMarker(names(idx)))
    }

  /** A named bind marker, quoted if the name calls for it */
  def bindMarker(name: String): String =
    if (Strings.needsDoubleQuotes(name)) s":${Strings.doubleQuote(name)}" else s":$name"

  /** Weaves the constant parts of a String Interpolation and its parameters back into a single
    * statement
    */
  def interleave(parts: Seq[String], params: Seq[String]): String =
    parts
      .zip(params)
      .foldLeft(new StringBuilder()) { case (acc, (part, param)) =>
        acc.append(part).append(param)
      }
      .append(parts.lastOption.getOrElse(""))
      .toString
}
