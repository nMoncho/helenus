/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

import org.slf4j.LoggerFactory

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
    def rowExtracted(): Unit                         = ()
    def rowLoaded(): Unit                            = ()
    def rangeCompleted(progress: RingProgress): Unit = ()
    def rangeFailed(error: Throwable): Unit          = ()
  }

  /** Logs migration progress as ranges complete, and logs range failures. This is
    * the reusable replacement for the PoC's `logProgress`, built on [[RingProgress]].
    *
    * Progress is logged only when the overall percentage advances by at least
    * `stepPercent`, so a fine-grained plan does not spam the log. Pass a custom `log`
    * to route messages somewhere other than the default logger (also handy for tests).
    */
  def logging(
      name: String,
      stepPercent: Int    = 5,
      log: String => Unit = defaultLog
  ): MigrationMetrics = new LoggingMetrics(name, stepPercent, log)

  /** Counts extracted rows, loaded rows, completed ranges, and failures, and tracks
    * the peak progress fraction. This generalizes the PoC's `countExtracted` and
    * `loaderReport`; read the totals off the returned instance after the run.
    *
    * If you do not want a metrics object at all, counting is also a one-line stream
    * recipe, for example folding the load side with `Sink.fold(0L)((n, _) => n + 1)`,
    * or `wireTap`-ing a counter onto the read source.
    */
  def counting(): CountingMetrics = new CountingMetrics

  /** Fans every callback out to each of `metrics`, so you can, for example, both log
    * progress and keep counts: `MigrationMetrics.all(logging("job"), counting())`.
    */
  def all(metrics: MigrationMetrics*): MigrationMetrics = new CompositeMetrics(metrics.toVector)

  private val defaultLogger = LoggerFactory.getLogger("net.nmoncho.helenus.migrations")

  private def defaultLog(message: String): Unit = defaultLogger.info(message)

  /** Counting metrics with accessible totals; see [[counting]]. */
  final class CountingMetrics extends MigrationMetrics {
    private val extractedCount = new AtomicLong(0)
    private val loadedCount    = new AtomicLong(0)
    private val rangesCount    = new AtomicLong(0)
    private val failedCount    = new AtomicLong(0)
    private val maxFraction    = new AtomicReference[Double](0.0)

    def extracted: Long       = extractedCount.get()
    def loaded: Long          = loadedCount.get()
    def rangesCompleted: Long = rangesCount.get()
    def failed: Long          = failedCount.get()
    def fraction: Double      = maxFraction.get()

    def rowExtracted(): Unit = { val _ = extractedCount.incrementAndGet() }
    def rowLoaded(): Unit    = { val _ = loadedCount.incrementAndGet() }

    def rangeCompleted(progress: RingProgress): Unit = {
      val _ = rangesCount.incrementAndGet()
      val _ = maxFraction.updateAndGet(current => math.max(current, progress.fraction))
    }

    def rangeFailed(error: Throwable): Unit = { val _ = failedCount.incrementAndGet() }
  }

  private final class LoggingMetrics(name: String, stepPercent: Int, log: String => Unit)
      extends MigrationMetrics {

    private val lastStep = new AtomicInteger(-1)

    def rowExtracted(): Unit = ()
    def rowLoaded(): Unit    = ()

    def rangeCompleted(progress: RingProgress): Unit = {
      val percentage = progress.fraction * 100.0
      val step       = if (stepPercent <= 0) percentage.toInt else percentage.toInt / stepPercent

      if (lastStep.getAndSet(step) != step) {
        log(f"[$name] progress $percentage%2.1f%%")
      }
    }

    def rangeFailed(error: Throwable): Unit =
      log(s"[$name] range failed: ${error.getMessage}")
  }

  private final class CompositeMetrics(delegates: Vector[MigrationMetrics])
      extends MigrationMetrics {
    def rowExtracted(): Unit                         = delegates.foreach(_.rowExtracted())
    def rowLoaded(): Unit                            = delegates.foreach(_.rowLoaded())
    def rangeCompleted(progress: RingProgress): Unit = delegates.foreach(_.rangeCompleted(progress))
    def rangeFailed(error: Throwable): Unit          = delegates.foreach(_.rangeFailed(error))
  }
}
