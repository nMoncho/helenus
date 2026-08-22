/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

/** Cassandra data migration primitives for Helenus.
  *
  * This module provides the building blocks for a Cassandra to Cassandra ETL
  * migration: extract from a source table, transform records, and load into a
  * target table, at full-table scale without read timeouts.
  *
  * It is organized in two layers:
  *   - a pure layer (the partitioner-agnostic token-range planner, and the
  *     progress and checkpoint models) with no stream-engine dependency, and
  *   - an executor layer, one per backend (Pekko, Akka, Monix, ZIO, Flink),
  *     each in its own subpackage and compiled against a `Provided` backend
  *     dependency, that turns a plan into that backend's native stream.
  */
package object migrations
