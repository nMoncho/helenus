/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import java.util.concurrent.atomic.AtomicReference

import _root_.net.nmoncho.helenus.api.RowMapper
import _root_.net.nmoncho.helenus.zio._
import _root_.zio.ZIO
import _root_.zio.stream.ZStream
import com.datastax.oss.driver.api.core.metadata.token.Token

/** ZIO executor for a token-range full-table scan.
  *
  * Turns a [[RingPlan]] into a `ZStream`, reading several ranges concurrently so
  * throughput is decoupled from the number of nodes. Each range is read with the
  * ZIO stream path (`streamValidated`), and the upper bound is normalized so the
  * wrap seam is not dropped (see [[TokenRing.normalizeUpperBound]]).
  *
  * Unlike the Pekko executor, this one does not set a token-aware routing token or
  * retry a timed-out range by splitting it: the ZIO stream operations bind and
  * execute internally and expose no routing or per-range read hook. Use a smaller
  * `splitsPerRange` in [[TokenRangePlanner]] to bound how much a single range reads,
  * and rely on the driver's own retry policy. Routing-aware reads and split retries
  * are available in the Pekko executor.
  */
package object zio {

  implicit class TokenRangeZioReadOps[Out](
      private val pstmt: ZScalaPreparedStatement2[Token, Token, Out]
  ) {

    /** Reads a whole table by scanning every range in `plan`.
      *
      * The prepared statement must be a bounded token-range query shaped like
      * `SELECT ... WHERE token(pk) > ? AND token(pk) <= ?`. Up to `parallelism`
      * ranges are read concurrently via `flatMapPar`, so a single process can
      * saturate the cluster rather than reading one range at a time. Elements are
      * emitted as `Chunk` pages, matching the rest of the ZIO API, and arrive
      * unordered across ranges.
      *
      * Ranges already recorded in `checkpoint` are skipped, and each range is marked
      * completed once it has been fully read, so a restart resumes rather than
      * re-scanning (see [[Checkpoint]]). A range is marked only after it is fully
      * read, never on failure, so a failed range is retried on the next run.
      *
      * @param plan        the ring scan plan from [[TokenRangePlanner]]
      * @param parallelism how many ranges to read at once (defaults to the number of
      *                    available processors); below 1 means 1
      * @param checkpoint which ranges to skip and where to record completions
      *                   (defaults to [[Checkpoint.None]], recording nothing)
      * @param metrics     observability callback for extracted rows and range
      *                    completions (defaults to a no-op)
      */
    def asTokenRangeStream(
        plan: RingPlan,
        parallelism: Int          = DefaultParallelism,
        checkpoint: Checkpoint    = Checkpoint.None,
        metrics: MigrationMetrics = MigrationMetrics.none
    )(implicit mapper: RowMapper[Out]): ZCqlStream[Out] =
      ZStream.unwrap {
        // Build the progress accumulator inside the effect so each run of the
        // returned stream starts from a fresh, correct progress state.
        ZIO.succeed {
          val progress = new AtomicReference(RingProgress(plan))

          ZStream
            .fromIterable(plan.splits)
            .flatMapPar(Math.max(1, parallelism)) { split =>
              if (checkpoint.isCompleted(split)) {
                // Already done on a previous run: advance progress but do not re-read.
                ZStream.execute(
                  ZIO.succeed(metrics.rangeCompleted(progress.updateAndGet(_.completing(split))))
                )
              } else {
                pstmt
                  .streamValidated(split.start, TokenRing.normalizeUpperBound(split.end))
                  .tap(chunk => ZIO.succeed(chunk.foreach(_ => metrics.rowExtracted())))
                  .concat(ZStream.execute(ZIO.succeed {
                    checkpoint.markCompleted(split)
                    metrics.rangeCompleted(progress.updateAndGet(_.completing(split)))
                  }))
              }
            }
        }
      }
  }
}
