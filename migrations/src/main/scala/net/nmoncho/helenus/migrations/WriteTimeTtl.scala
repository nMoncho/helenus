/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations

/** A row-level write timestamp and TTL to preserve on the target.
  *
  * A plain migration writes with a fresh server timestamp and no TTL, which
  * rewrites every cell's timestamp (breaking last-write-wins) and drops expiry.
  * To preserve them, read `WRITETIME(col)` and `TTL(col)` on the source and write
  * the target with `USING TIMESTAMP ? AND TTL ?`, for example:
  *
  * {{{
  * // read
  * SELECT id, v, WRITETIME(v) AS wt_v, TTL(v) AS ttl_v FROM source WHERE ...
  * // write
  * INSERT INTO target (id, v) VALUES (?, ?) USING TIMESTAMP ? AND TTL ?
  * }}}
  *
  * Cassandra tracks WRITETIME and TTL per cell, not per row, so a row-level
  * migration cannot preserve them exactly unless it writes column by column.
  * [[WriteTimeTtl.rowLevel]] computes one honest approximation for a whole row.
  *
  * @param timestamp write timestamp in microseconds since epoch, for `USING TIMESTAMP`
  * @param ttl       remaining TTL in seconds for `USING TTL`; `None` means no expiry
  */
final case class WriteTimeTtl(timestamp: Long, ttl: Option[Int]) {

  /** TTL value to bind to `USING TTL ?`. Cassandra treats `0` as "no expiry", so a
    * row with no TTL binds `0`.
    */
  def ttlOrZero: Int = ttl.getOrElse(0)
}

object WriteTimeTtl {

  /** Combines per-cell writetimes and TTLs into one row-level value.
    *
    * Uses the maximum writetime (last write wins) and the smallest positive TTL, so
    * the migrated row expires no later than its earliest-expiring source cell. A
    * cell with no TTL is represented by `None` (or a non-positive value) and is
    * ignored for the minimum; if no cell has a positive TTL the result has
    * `ttl = None`.
    *
    * @throws IllegalArgumentException if `writetimes` is empty
    */
  def rowLevel(writetimes: Iterable[Long], ttls: Iterable[Option[Int]]): WriteTimeTtl = {
    require(writetimes.nonEmpty, "at least one writetime is required to preserve a row")

    val positiveTtls = ttls.flatten.filter(_ > 0)

    WriteTimeTtl(
      timestamp = writetimes.max,
      ttl       = if (positiveTtls.isEmpty) None else Some(positiveTtls.min)
    )
  }
}
