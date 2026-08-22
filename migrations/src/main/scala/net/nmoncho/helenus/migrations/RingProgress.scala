/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

/** Immutable migration progress, as a fraction of the token ring scanned.
  *
  * Weights come from the [[RingPlan]] (see [[RangeSplit.weight]]), so the fraction
  * is ring coverage, not row count. Fold each completed [[RangeSplit]] in with
  * [[completing]]; the value is a plain immutable snapshot, unlike the PoC's shared
  * mutable `AtomicReference` tracker.
  *
  * @param totalWeight        total ring weight to scan
  * @param completedWeight    ring weight scanned so far
  * @param totalByReplica     ring weight owned by each replica
  * @param completedByReplica ring weight scanned so far per replica
  */
final case class RingProgress(
    totalWeight: BigDecimal,
    completedWeight: BigDecimal,
    totalByReplica: Map[String, BigDecimal],
    completedByReplica: Map[String, BigDecimal]
) {

  /** Overall progress in `[0.0, 1.0]`. */
  def fraction: Double = RingProgress.fractionOf(completedWeight, totalWeight)

  /** Per-replica progress in `[0.0, 1.0]`, keyed as in [[RingProgress.replicaKey]]. */
  def fractionByReplica: Map[String, Double] =
    totalByReplica.map { case (replica, total) =>
      replica -> RingProgress.fractionOf(
        completedByReplica.getOrElse(replica, BigDecimal(0)),
        total
      )
    }

  /** Returns a new snapshot with `split`'s weight folded into the totals. */
  def completing(split: RangeSplit): RingProgress = {
    val replica = RingProgress.replicaKey(split)

    copy(
      completedWeight    = completedWeight + split.weight,
      completedByReplica = completedByReplica.updated(
        replica,
        completedByReplica.getOrElse(replica, BigDecimal(0)) + split.weight
      )
    )
  }
}

object RingProgress {

  /** Replica key used when a split has no resolved replica (no keyspace set). */
  final val Unassigned = "unassigned"

  /** Starts progress at zero for the given plan, precomputing the per-replica totals. */
  def apply(plan: RingPlan): RingProgress = {
    val totalByReplica =
      plan.splits.groupBy(replicaKey).map { case (replica, splits) =>
        replica -> splits.foldLeft(BigDecimal(0))(_ + _.weight)
      }

    RingProgress(plan.totalWeight, BigDecimal(0), totalByReplica, Map.empty)
  }

  /** A stable string key for a split's owning replica. */
  def replicaKey(split: RangeSplit): String =
    split.replica.map(_.getEndPoint.toString).getOrElse(Unassigned)

  private def fractionOf(done: BigDecimal, total: BigDecimal): Double =
    if (total <= 0) 1.0 else (done / total).toDouble.min(1.0)
}
