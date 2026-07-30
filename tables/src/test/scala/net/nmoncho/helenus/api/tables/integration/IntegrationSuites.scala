/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables.integration

import org.scalatest.Suites

/** Runs the integration specs sequentially against one shared embedded
  * Cassandra. The nested specs are `@DoNotDiscover` so sbt cannot run them in
  * parallel, which would clash on the shared keyspaces.
  *
  * Run only these with: `sbt "testOnly com.example.cql.integration.IntegrationSuites"`.
  * Run only the fast unit specs with: `sbt "testOnly com.example.cql.*"`.
  */
class IntegrationSuites
    extends Suites(
//      new BindMarkerSpec,
//      new ComputedColumnIntegrationSpec,
//      new DdlIntegrationSpec,
//      new DeleteIntegrationSpec,
      new IndexIntegrationSpec
//      new InsertIntegrationSpec,
//      new SelectExecuteIntegrationSpec,
//      new UpdateIntegrationSpec
    )
