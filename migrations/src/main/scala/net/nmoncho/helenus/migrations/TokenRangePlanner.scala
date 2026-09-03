/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.metadata.TokenMap
import com.datastax.oss.driver.api.core.metadata.token.Token
import com.datastax.oss.driver.api.core.metadata.token.TokenRange
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3Token
import com.datastax.oss.driver.internal.core.metadata.token.Murmur3TokenRange

/** One planned token range to scan.
  *
  * Token ranges are lower-bound exclusive and upper-bound inclusive, so a
  * [[RangeSplit]] maps to a query shaped like
  * `WHERE token(pk) > start AND token(pk) <= end`.
  *
  * @param range   the driver token range to scan
  * @param replica a replica that owns the range, used later for token-aware
  *                routing and per-replica parallelization; `None` when no
  *                keyspace is set on the session, so replicas cannot be resolved
  * @param weight  fraction of the whole ring covered by this range, in `(0, 1]`;
  *                the weights across a [[RingPlan]] sum to approximately `1.0`
  */
final case class RangeSplit(range: TokenRange, replica: Option[Node], weight: BigDecimal) {

  /** Lower bound of the range (exclusive). */
  def start: Token = range.getStart

  /** Upper bound of the range (inclusive). */
  def end: Token = range.getEnd
}

/** A full-ring scan plan: the whole token ring partitioned into ranges, each
  * covered exactly once.
  *
  * Unlike a per-node enumeration (which repeats each range once per replica and
  * would therefore read every row `replication_factor` times), a [[RingPlan]]
  * enumerates each ring range a single time and assigns it to one replica.
  */
final case class RingPlan(splits: Vector[RangeSplit]) {

  /** Groups the splits by the replica that owns them, so an executor can run one
    * substream per replica. Splits with no resolved replica land under `None`.
    */
  def byReplica: Map[Option[Node], Vector[RangeSplit]] = splits.groupBy(_.replica)

  /** Sum of all split weights, approximately `1.0` for a complete plan. */
  def totalWeight: BigDecimal = splits.foldLeft(BigDecimal(0))(_ + _.weight)

  /** Number of ranges in the plan. */
  def size: Int = splits.size

  def isEmpty: Boolean = splits.isEmpty
}

/** Builds a partitioner-agnostic [[RingPlan]] for a full-table token-range scan.
  *
  * The plan is pure driver-metadata math with no stream-engine dependency, so it
  * can be consumed by any backend executor. It relies on the driver's own
  * `TokenRange.splitEvenly` and `unwrap`, which are partitioner-agnostic, and it
  * computes each range's weight per partitioner. Murmur3 and Random are measured
  * exactly; any other partitioner (for example ByteOrdered) degrades gracefully
  * to uniform weighting rather than throwing, so unknown partitioners still yield
  * a usable plan.
  *
  * ==Binding the range bounds==
  *
  * A [[RangeSplit]] is meant to bind a query shaped like
  * `WHERE token(pk) > ? AND token(pk) <= ?`. There is no need to hand-roll a
  * token codec: `import net.nmoncho.helenus._` brings the core `TypeCodec[Token]`
  * into implicit scope, and it encodes every partitioner's token by dispatching
  * on the concrete token type. So `query.prepare[Token, Token]` binds
  * [[RangeSplit.start]] and [[RangeSplit.end]] directly.
  *
  * One boundary needs care: the range whose end is the ring's minimum token
  * represents "up to the maximum token", so its upper bound must be left open
  * (`WHERE token(pk) > ?` only). A backend executor handles this seam when it
  * renders each range into a query.
  */
object TokenRangePlanner {

  /** Builds a scan plan for the session's current keyspace.
    *
    * @param splitsPerRange how many sub-ranges to split each ring range into;
    *                       values below `1` are treated as `1`
    */
  def plan(splitsPerRange: Int = 1)(implicit session: CqlSession): RingPlan =
    session.getMetadata.getTokenMap.toScala match {
      case Some(tokenMap) if !tokenMap.getTokenRanges.isEmpty =>
        planFrom(tokenMap, Math.max(1, splitsPerRange), session.getKeyspace.toScala)

      case _ =>
        wholeRing
    }

  /** Builds a scan plan for the session's current keyspace.
    *
    * @param splitsPerRange how many sub-ranges to split each ring range into;
    *                       values below `1` are treated as `1`
    */
  def planAsync(
      splitsPerRange: Int = 1
  )(implicit session: Future[CqlSession], ec: ExecutionContext): Future[RingPlan] =
    session.map(implicit s => plan(splitsPerRange))

  /** Fallback single-range plan covering the whole ring, used when the token map
    * is unavailable (for example when token metadata is disabled). It assumes the
    * Murmur3 partitioner, the Cassandra default, since without a token map the
    * partitioner cannot be determined from metadata.
    */
  def wholeRing(implicit session: CqlSession): RingPlan = {
    val _ = session
    RingPlan(Vector(RangeSplit(EntireMurmur3Ring, None, BigDecimal(1))))
  }

  private def planFrom(
      tokenMap: TokenMap,
      splitsPerRange: Int,
      keyspace: Option[CqlIdentifier]
  ): RingPlan = {
    val ranges: Vector[TokenRange] =
      tokenMap.getTokenRanges.asScala.toVector.sorted
        .flatMap(subdivide(_, splitsPerRange))

    val math = RingMath.forRanges(ranges)

    val splits = ranges.map { range =>
      val replica = keyspace.flatMap(ks => tokenMap.getReplicas(ks, range).asScala.headOption)
      RangeSplit(range, replica, math.weight(range))
    }

    RingPlan(splits)
  }

  /** Splits a ring range into `splitsPerRange` sub-ranges, unwraps any that wrap
    * around the ring, and drops empty ranges, so every result is a plain
    * `(start, end]` range usable in a range query.
    */
  private def subdivide(range: TokenRange, splitsPerRange: Int): Vector[TokenRange] =
    range
      .splitEvenly(splitsPerRange)
      .asScala
      .toVector
      .flatMap(sub => if (sub.isEmpty) Vector(sub) else sub.unwrap().asScala.toVector)
      .filterNot(_.isEmpty)

  private implicit val tokenRangeOrdering: Ordering[TokenRange] =
    Ordering.fromLessThan((a, b) => a.compareTo(b) < 0)

  private val EntireMurmur3Ring: TokenRange =
    new Murmur3TokenRange(new Murmur3Token(Long.MinValue), new Murmur3Token(Long.MinValue))
}
