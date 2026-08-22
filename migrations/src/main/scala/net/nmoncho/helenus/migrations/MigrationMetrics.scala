/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

/** Observability callback for a migration, so operators can bridge to Micrometer,
  * Dropwizard, or logs without the library taking a metrics dependency.
  *
  * The executor invokes [[rowExtracted]] per row read, [[rangeCompleted]] when a
  * range finishes (with the updated ring progress), and [[rangeFailed]] when a
  * range ultimately fails after retries. [[rowLoaded]] is invoked by the write
  * stage of the pipeline, since the executor only reads.
  *
  * Implementations must be safe to call concurrently: several ranges are read at
  * once. The default is [[MigrationMetrics.none]], which does nothing.
  */
trait MigrationMetrics {

  /** One row was read from the source. */
  def rowExtracted(): Unit

  /** One row was written to the target. Called from the load stage, not the executor. */
  def rowLoaded(): Unit

  /** A range finished; `progress` is the updated overall and per-replica coverage. */
  def rangeCompleted(progress: RingProgress): Unit

  /** A range ultimately failed after retries were exhausted. */
  def rangeFailed(error: Throwable): Unit
}

object MigrationMetrics {

  /** A metrics sink that records nothing. */
  val none: MigrationMetrics = new MigrationMetrics {
    def rowExtracted(): Unit                          = ()
    def rowLoaded(): Unit                             = ()
    def rangeCompleted(progress: RingProgress): Unit  = ()
    def rangeFailed(error: Throwable): Unit           = ()
  }
}
