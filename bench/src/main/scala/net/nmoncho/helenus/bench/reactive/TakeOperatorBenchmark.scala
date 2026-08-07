/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package bench
package reactive

import java.util.concurrent.TimeUnit

import net.nmoncho.helenus.internal.reactive.TakeOperator
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

/** Overhead of [[TakeOperator]], including its upstream cancellation.
  *
  * `baseline` consumes exactly `elements` from a source of that size; `bench` takes `elements` from
  * a source of twice that size, so the operator must cancel the upstream halfway through. The delta
  * captures the take bookkeeping plus the cancellation path.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
class TakeOperatorBenchmark {

  @Param(Array("10", "100", "1000"))
  private var elements = 0

  @Benchmark
  def baseline(blackHole: Blackhole): Unit =
    new SyncRangePublisher(elements).subscribe(new CountingSubscriber[Integer](blackHole))

  @Benchmark
  def bench(blackHole: Blackhole): Unit = {
    val op = new TakeOperator[Integer](new SyncRangePublisher(elements * 2), elements)

    op.publisher.subscribe(new CountingSubscriber[Integer](blackHole))
  }
}
