# helenus-migrations

Building blocks for Cassandra to Cassandra data migrations, following an ETL
approach: extract from a source table with a token-range full-table scan, transform
each row, and load into a target table.

The token-range planner lives in `net.nmoncho.helenus.migrations`. The per-backend
executors live in `net.nmoncho.helenus.migrations.pekko` and, for Flink, reuse
`net.nmoncho.helenus.flink`'s `CassandraSource`. A whole migration is one call,
`asTokenRangeMigration(...)`, which extracts, transforms, and loads with full
observability. Runnable example apps live under the `example` package.

## Operations

### Rate limiting and backpressure

There is no default rate cap: a migration runs as fast as the cluster and stream
backpressure allow, and the reactive write sink already keeps the source from
outrunning the sink. Add a cap only to deliberately protect the source or target
cluster, by passing a `RateLimit(elements, per)`:

```scala
readStmt.asTokenRangeMigration(plan, transform, sink, rateLimit = Some(RateLimit(1000, 1.second)))
```

Tune `elements` per `per` against the target's spare write capacity, not the client.
The goal is to keep the target healthy (p99 latency, pending compactions) while the
migration runs alongside production traffic, so start conservative and raise it while
watching the target. Because it is a single throttle on a linear pipeline, it bounds
end-to-end throughput, so the reads slow to match, which also protects the source.

### Large partitions, collections, and tombstones

Token-range splitting bounds how many rows a single range query returns, but it does
not bound a single very large partition. If reads time out on wide ranges:

- Raise `splitsPerRange` in `TokenRangePlanner.plan(...)` so each range query reads
  fewer rows. This also gives finer checkpoint and retry granularity.
- The executor already retries a timed-out range by splitting it in half, down to a
  configurable minimum (`RetryPolicy`), so a transient oversized read recovers
  without failing the whole scan.

On the write side:

- Prefer append-friendly writes. Rewriting a full collection column per row generates
  tombstones on the target, which slow later reads until compaction, so migrate a
  collection as it is rather than clearing and re-adding its elements.
- Plain `INSERT` and `UPDATE` upserts are idempotent by primary key, so a restart, or
  a retry that re-reads part of a range, is safe. Counter updates and deletes are not
  idempotent and need explicit handling.
- Preserve `WRITETIME` and `TTL` when the target must keep last-write-wins semantics
  and cell expiry. See `WriteTimeTtl`.

### Restart

A long migration can resume from a `Checkpoint` (in-memory, file, or a Cassandra
table), skipping the ranges a previous run completed. On Flink the token-range source
is checkpointed natively, so a savepoint resumes without re-reading finished splits.

### Observability

Pass a `MigrationMetrics` to report extracted rows, loaded rows, per-range progress,
and range failures. `MigrationMetrics.logging(...)` logs progress, `counting()` keeps
running totals, and `all(...)` combines them. On Flink, use Flink's own metrics.
