/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

/** Fixtures for [[CqlQueryInterpolationDiagnosticsSpec]].
  *
  * These need to be real, separately-compiled top-level members (not declared inline inside a
  * `ToolBox`-parsed snippet) because the diagnostics distinguish a `val` member of a module from
  * a local/parameter by checking `Symbol.isStatic`, which only holds for members of a real
  * top-level (or nested-in-object) module — a snippet wrapped by `ToolBox` for evaluation does
  * not qualify.
  */
object CqlQueryInterpolationDiagnosticsFixtures {

  // Intentionally widened via an explicit type annotation: this is the pitfall the "widened
  // constant" diagnostic targets. See `net.nmoncho.helenus.Keyspace.ChargingV5` for the same
  // note in the non-broken form.
  final val widenedTableName: String = "the_table"

  // Not widened: its type stays the literal singleton type, so scalac constant-folds it away
  // before the macro even runs.
  final val plainPvin = "p_vin"

  var mutableThing: String = "the_table"
}
