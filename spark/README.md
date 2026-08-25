# Helenus Spark

`helenus-spark` complements the [spark-cassandra-connector](https://github.com/apache/cassandra-spark-connector)
rather than competing with it. The connector keeps ownership of every distributed-scale
concern — token-range splitting, partitioner awareness, predicate pushdown, the DataSource
V2 (Catalyst) source, and RDD/DataFrame integration — while Helenus contributes only what
it is uniquely good at: CQL-first, type-safe statement building and codec-based mapping.

> **Setup, the compatibility matrix, and mdoc-checked examples are still to come** (docs
> workstream). The sections below cover the structured (DataFrame / Dataset) boundary and
> the connector pass-throughs.

## Read path (RDD)

`sc.cassandraTable[T]` returns an `RDD[T]` whose rows are mapped by Helenus codecs; the
connector still does the token-aware scan. Bind the Helenus `RowReaderFactory` from the
package-object entry point next to the call:

```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.spark._
import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.reader.RowReaderFactory

implicit val hotels: RowReaderFactory[Hotel] = helenusRowReaderFactory(Hotel.rowMapper)
val rdd = sc.cassandraTable[Hotel]("hotels_ks", "hotels")
```

Pass the mapper derivation itself (a companion `val`, `RowMapper.of[T]`, or
`RowMapper.cached[T]()`), never a value captured from a local scope: a derived mapper closes
over non-serializable driver codecs, so the bridge re-derives it on the executor rather than
shipping it. Scanning, splitting, and distribution are the connector's; there is no Helenus
`RDD` subclass. For client-side ranged reads, see the migration tooling instead.

## Write path (CQL-first sink)

`foreachPartitionCql` is the primary, recommended typed write path. It runs a user-provided
`CqlSession => ScalaPreparedStatement[In, Out]` and binds each record with compile-time
bind-arity safety, so it can express what `saveToCassandra` cannot: LWT / conditional writes
(`IF NOT EXISTS`, `IF …`), custom-`WHERE` updates and deletes, and arbitrary CQL.

```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.spark._
import net.nmoncho.helenus.spark.sink.CassandraSink

hotels
  .map(h => (h.id, h.name, h.phone, h.address, h.pois))
  .foreachPartitionCql(
    "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS"
      .toCQL(_)
      .prepare[String, String, String, Address, Set[String]],
    CassandraSink.Config()
  )
```

The statement is prepared once per partition inside the connector's session (obtained via
`CassandraConnector.withSessionDo`; the module never opens its own session). The write path
has **at-least-once** semantics — Spark re-runs a failed partition and this path bypasses the
connector's `WriteConf` — so keep statements idempotent (`IF NOT EXISTS` and plain upserts
are safe to replay) or set `CassandraSink.Config(idempotent = false)` for counters and
non-idempotent conditionals and design for a replay. A configured `failureHandler` sees any
write failure before it is rethrown; failures are never silently swallowed.

## DataFrame and Dataset boundary

DataFrame read and write — including predicate pushdown and the Catalyst source — are the
connector's, used unchanged. Helenus does not wrap them, and typed Catalyst encoders are out
of scope (building them would re-enter the DataSource V2 territory this module refuses to
compete in).

```scala
// DataFrame access is the connector's format, used verbatim:
val df = spark.read
  .format("org.apache.spark.sql.cassandra")
  .options(Map("table" -> "hotels", "keyspace" -> "hotels_ks"))
  .load()
```

The only typed bridge is a thin `Dataset[T].foreachPartitionCql`, which forwards to the RDD
sink via `Dataset.rdd`. Because the element type must equal the statement's `In`, use
`.prepareFrom[T]` for a domain object (its `Mapping[T]` binds the record) — and bind that
`Mapping[T]` as a stable global (a companion or object `val`), never a local val, so the
builder re-accesses it on the executor instead of shipping the non-serializable mapping:

```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.spark._
import net.nmoncho.helenus.spark.sink.CassandraSink

implicit val hotelMapping: Mapping[Hotel] = Mapping[Hotel]() // in a companion / object

val ds: org.apache.spark.sql.Dataset[Hotel] = // ...
ds.foreachPartitionCql(
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
    .toCQL(_)
    .prepareFrom[Hotel],
  CassandraSink.Config()
)
```

### ⚠️ Known limitation: ANTLR conflict with Spark SQL

The structured path above (any DataFrame/Dataset operation, and therefore the `Dataset`
bridge) requires **Spark SQL**, which runs its Catalyst SQL parser generated with **ANTLR
4.9.3**. Helenus core depends on **ANTLR 4.13.2** for its compile-time `toCQL` grammar, and
because both use the same `org.antlr:antlr4-runtime` coordinate, the higher version wins on a
combined classpath. Spark SQL's parser then fails to initialize
(`InvalidClassException: … ATN … version 3 (expected 4)`), which breaks every DataFrame /
Dataset operation.

There is no single ANTLR version that satisfies both: the compile-time `toCQL` macro needs
4.13.2 (its `CqlLexer` ATN is version 4) and Spark SQL needs 4.9.3 at runtime (its parser ATN
is version 3). The **RDD paths above are unaffected** — they never invoke Spark's SQL parser —
so `cassandraTable[T]` and `foreachPartitionCql` on an `RDD` work today.

#### Workaround (spark-submit / cluster deployments)

Helenus uses ANTLR **only at compile time** (the `toCQL` grammar); nothing on the runtime
path touches it. So keep 4.13.2 on the *compile* classpath and let **Spark provide 4.9.3 at
runtime**:

1. Keep Spark and the connector `provided` — on a cluster they are already on the runtime
   classpath, and they bring ANTLR 4.9.3 with them.
2. Keep `antlr4-runtime` **out of your application assembly** so the fat-jar does not ship
   Helenus's 4.13.2 over Spark's 4.9.3. With `sbt-assembly`:

   ```scala
   // ANTLR is compile-time-only for Helenus; let Spark's 4.9.3 win at runtime.
   assembly / assemblyExcludedJars := {
     val cp = (assembly / fullClasspath).value
     cp.filter(_.data.getName.startsWith("antlr4-runtime"))
   }
   ```

   (Maven/Gradle: exclude `org.antlr:antlr4-runtime` from the shaded/runtime artifact the
   same way — compile still sees it transitively from `helenus-core`.)

Your code compiles against 4.13.2 (the `toCQL` macro works), and on the cluster Spark SQL
runs against its own 4.9.3, so DataFrames, Datasets, and the `Dataset` bridge work.

This does **not** help a self-contained local run that embeds Spark in the *same* JVM it was
compiled in (both versions land on one classpath, e.g. this module's own tests) — there,
forcing 4.9.3 would break the `toCQL` macro. That case, and removing the workaround
altogether, is what shading resolves:

#### Fix (future): shade ANTLR in helenus-core

Relocating `org.antlr.v4.runtime` inside `helenus-core` (the way the driver relocates
Netty/Guava/Jackson) makes Helenus's grammar and Spark SQL's parser use separate copies, so
the two never collide and no workaround is needed. Until then, treat the `Dataset` bridge as
provided-but-gated on that change.

## Connector operations, used directly

Joins, replica-aware repartitioning, and the other RDD operations are the connector's and are
used unchanged — Helenus ships no wrappers for them:

- `joinWithCassandraTable[R]` / `leftJoinWithCassandraTable[R]`
- `repartitionByCassandraReplica`
- `perPartitionLimit`
- `spanBy`

The read bridge composes with joins for free: `joinWithCassandraTable[R]` resolves the same
implicit `RowReaderFactory[R]`, so a Helenus factory bound in scope maps the joined rows
through Helenus codecs with no extra wrapper.
