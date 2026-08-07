/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package bench
package reactive

import java.util.concurrent.TimeUnit

import net.nmoncho.helenus.internal.reactive.MapOperator
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/** Overhead of wrapping a [[org.reactivestreams.Publisher]] with [[MapOperator]].
  *
  * `baseline` consumes the source directly; `bench` routes it through a `MapOperator` applying a
  * trivial mapping. The delta is the per-element cost the operator adds.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class MapOperatorBenchmark {

  @Param(Array("10", "100", "1000"))
  private var elements = 0

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    new SyncRangePublisher(elements).subscribe(new CountingSubscriber[Integer](blackHole))

  @Benchmark
  def bench(blackHole: Blackhole): Unit = {
    val op = new MapOperator[Integer, Integer](
      new SyncRangePublisher(elements),
      (i: Integer) => Integer.valueOf(i + 1)
    )

    op.publisher.subscribe(new CountingSubscriber[Integer](blackHole))
  }
}
