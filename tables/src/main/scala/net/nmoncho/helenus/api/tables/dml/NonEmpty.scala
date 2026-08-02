/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables
package dml

import scala.annotation.implicitNotFound

import shapeless.::
import shapeless.HList

/** Evidence that an `HList` is non-empty (it has no instance for `HNil`).
  *
  * Used by the UPDATE builder to require at least one SET assignment before
  * `execute` / `prepare` / `prepareAsync`. INSERT uses the stronger
  * [[where.CanInsert]] gate instead (which requires the whole primary key, and
  * thus implies non-emptiness), so it does not need this witness.
  */
@implicitNotFound(
  "This UPDATE has no SET assignments and cannot be executed. " +
    "CQL requires an UPDATE to set at least one column: add a set(...) " +
    "before calling execute, prepare, or prepareAsync."
)
sealed trait NonEmpty[L <: HList]

object NonEmpty {

  implicit def cons[H, T <: HList]: NonEmpty[H :: T] = new NonEmpty[H :: T] {}
}
