# helenus-spark

Apache Spark integration for Helenus. `helenus-spark` complements the
[spark-cassandra-connector](https://github.com/apache/cassandra-spark-connector)
rather than competing with it. The connector keeps ownership of every distributed-scale
concern (token-range splitting, partitioner awareness, predicate pushdown, the DataSource
V2 Catalyst source, and RDD/DataFrame integration), while Helenus contributes only what it
is uniquely good at: CQL-first, type-safe statement building and codec-based mapping.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-spark" % helenusVersion
```

Spark, the connector, and the DataStax driver are `provided`, so add them alongside:

```scala
libraryDependencies ++= Seq(
  "org.apache.spark"   %% "spark-core"                % "3.5.1" % Provided,
  "org.apache.spark"   %% "spark-sql"                 % "3.5.1" % Provided,
  "com.datastax.spark" %% "spark-cassandra-connector" % "3.5.1" % Provided
)
```

On a cluster these are already on the runtime classpath. The connector ships the driver as
its own shaded `java-driver-core`, and that single copy supplies the un-relocated
`com.datastax.oss.driver.*` API that Helenus statements and codecs run against, so do not
add an unshaded `java-driver-core` to the runtime classpath (see
[Compatibility](#compatibility) and the ANTLR note below for the one caveat that affects
Spark SQL).

## Usage

The examples below share this domain model. Derive the codecs once and keep them as stable,
top-level vals: the bridges re-access them on the executor rather than shipping them, because
a derived mapper closes over non-serializable driver codecs and must be re-derivable by name
instead of captured from a local scope.

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec

import net.nmoncho.helenus._
import net.nmoncho.helenus.api.cql.Mapping

final case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)
object Address {
  // In the type's own companion, so it is in implicit scope for every RowMapper, Mapping,
  // and bound statement that touches an Address.
  implicit val codec: TypeCodec[Address] = Codec.of[Address]()
}

final case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])
object Hotel {
  implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()
  implicit val mapping: Mapping[Hotel]     = Mapping[Hotel]()
}
```

### Read path (RDD)

`sc.cassandraTable[T]` returns an `RDD[T]` whose rows are mapped by Helenus codecs; the
connector still does the token-aware scan. Bind the Helenus `RowReaderFactory` from the
package-object entry point next to the call:

```scala mdoc:compile-only
import org.apache.spark.SparkContext

import com.datastax.spark.connector._
import com.datastax.spark.connector.rdd.reader.RowReaderFactory
import net.nmoncho.helenus.spark._

def readHotels(sc: SparkContext) = {
  // A locally bound implicit outranks the connector's own default RowReaderFactory, so the
  // scan resolves this factory and maps each row through Helenus codecs. Pass the mapper
  // derivation by name (a companion val here), never a value captured from a local scope.
  implicit val hotels: RowReaderFactory[Hotel] = helenusRowReaderFactory(Hotel.rowMapper)

  sc.cassandraTable[Hotel]("hotels_ks", "hotels")
}
```

Scanning, splitting, and distribution are the connector's; there is no Helenus `RDD`
subclass. For client-side ranged reads, see the migration tooling instead. A global
`implicit def` is deliberately not provided: it would silently override the connector's
default for every type that has a `RowMapper`, and could not be made both seamless and
serializable.

### Write path (CQL-first sink)

`foreachPartitionCql` is the primary, recommended typed write path. It runs a user-provided
`CqlSession => ScalaPreparedStatement[In, Out]` and binds each record with compile-time
bind-arity safety, so it can express what `saveToCassandra` cannot: LWT and conditional
writes (`IF NOT EXISTS`, `IF ...`), custom-`WHERE` updates and deletes, and arbitrary CQL.
The RDD element type must equal the statement's `In`, so map to the bind tuple first and use
a multi-arg `.prepare[...]`:

```scala mdoc:compile-only
import org.apache.spark.rdd.RDD

import net.nmoncho.helenus._
import net.nmoncho.helenus.spark._
import net.nmoncho.helenus.spark.sink.CassandraSink

def writeHotels(hotels: RDD[Hotel]): Unit =
  hotels
    .map(h => (h.id, h.name, h.phone, h.address, h.pois))
    .foreachPartitionCql(
      "INSERT INTO hotels_ks.hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS"
        .toCQL(_)
        .prepare[String, String, String, Address, Set[String]],
      CassandraSink.Config()
    )
```

The statement is prepared once per partition inside the connector's session (obtained via
`CassandraConnector.withSessionDo`; the module never opens its own session). The write path
has at-least-once semantics, because Spark re-runs a failed partition and this path bypasses
the connector's `WriteConf`, so keep statements idempotent (`IF NOT EXISTS` and plain upserts
are safe to replay) or set `CassandraSink.Config(idempotent = false)` for counters and
non-idempotent conditionals and design for a replay. A configured `failureHandler` sees any
write failure before it is rethrown; failures are never silently swallowed.

`CassandraSink.Config` tunes only the Helenus sink and never duplicates connector keys (the
session is the connector's, driven by the `spark.cassandra.*` keys in the `SparkConf`):

- `batchSize` (default `1`) groups that many bound statements into one `UNLOGGED` batch per
  execute. Keep it at `1` for LWT and conditional writes, which cannot be batched, and raise
  it only for many small same-partition writes.
- `idempotent` (default `true`) is applied to every statement via `setIdempotent`. Set it to
  `false` for counters and non-idempotent conditional writes.
- `failureHandler` (default no-op) is invoked with a write failure before it is rethrown.

### DataFrame and Dataset boundary

DataFrame read and write, including predicate pushdown and the Catalyst source, are the
connector's, used unchanged. Helenus does not wrap them, and typed Catalyst encoders are out
of scope (building them would re-enter the DataSource V2 territory this module refuses to
compete in):

```scala mdoc:compile-only
import org.apache.spark.sql.{ DataFrame, SparkSession }

def hotelsDataFrame(spark: SparkSession): DataFrame =
  spark.read
    .format("org.apache.spark.sql.cassandra")
    .options(Map("table" -> "hotels", "keyspace" -> "hotels_ks"))
    .load()
```

The only typed bridge is a thin `Dataset[T].foreachPartitionCql`, which forwards to the RDD
sink via `Dataset.rdd`. Because the element type must equal the statement's `In`, use
`.prepareFrom[T]` for a domain object (its `Mapping[T]` binds the record), and keep that
`Mapping[T]` a stable global (the companion val defined in the model above), never a local
val, so the builder re-accesses it on the executor instead of shipping the non-serializable
mapping:

```scala mdoc:compile-only
import org.apache.spark.sql.Dataset

import net.nmoncho.helenus._
import net.nmoncho.helenus.spark._

def writeDataset(ds: Dataset[Hotel]): Unit =
  ds.foreachPartitionCql(
    "INSERT INTO hotels_ks.hotels(id, name, phone, address, pois) VALUES (?, ?, ?, ?, ?)"
      .toCQL(_)
      .prepareFrom[Hotel]
  )
```

#### Known limitation: ANTLR conflict with Spark SQL

The structured path above (any DataFrame or Dataset operation, and therefore the `Dataset`
bridge) requires Spark SQL, whose Catalyst parser is generated with ANTLR 4.9.3. Helenus core
depends on ANTLR 4.13.2 for its compile-time `toCQL` grammar, and because both use the same
`org.antlr:antlr4-runtime` coordinate, the higher version wins on a combined classpath. Spark
SQL's parser then fails to initialize (`InvalidClassException: ... ATN ... version 3 (expected
4)`), which breaks every DataFrame and Dataset operation.

There is no single ANTLR version that satisfies both: the compile-time `toCQL` macro needs
4.13.2 (its `CqlLexer` ATN is version 4) and Spark SQL needs 4.9.3 at runtime (its parser ATN
is version 3). The RDD paths above are unaffected, because they never invoke Spark's SQL
parser, so `cassandraTable[T]` and `foreachPartitionCql` on an `RDD` work today.

##### Workaround (spark-submit and cluster deployments)

Helenus uses ANTLR only at compile time (the `toCQL` grammar); nothing on the runtime path
touches it. So keep 4.13.2 on the compile classpath and let Spark provide 4.9.3 at runtime:

1. Keep Spark and the connector `provided`. On a cluster they are already on the runtime
   classpath, and they bring ANTLR 4.9.3 with them.
2. Keep `antlr4-runtime` out of your application assembly so the fat jar does not ship
   Helenus's 4.13.2 over Spark's 4.9.3. With `sbt-assembly`:

   ```scala
   // ANTLR is compile-time-only for Helenus; let Spark's 4.9.3 win at runtime.
   assembly / assemblyExcludedJars := {
     val cp = (assembly / fullClasspath).value
     cp.filter(_.data.getName.startsWith("antlr4-runtime"))
   }
   ```

   (Maven and Gradle: exclude `org.antlr:antlr4-runtime` from the shaded or runtime artifact
   the same way; compile still sees it transitively from `helenus-core`.)

Your code compiles against 4.13.2 (the `toCQL` macro works), and on the cluster Spark SQL
runs against its own 4.9.3, so DataFrames, Datasets, and the `Dataset` bridge work.

This does not help a self-contained local run that embeds Spark in the same JVM it was
compiled in (both versions land on one classpath, for example this module's own tests). There,
forcing 4.9.3 would break the `toCQL` macro. That case, and removing the workaround
altogether, is what shading resolves.

##### Fix (future): shade ANTLR in helenus-core

Relocating `org.antlr.v4.runtime` inside `helenus-core` (the way the driver relocates Netty,
Guava, and Jackson) makes Helenus's grammar and Spark SQL's parser use separate copies, so the
two never collide and no workaround is needed. Until then, treat the `Dataset` bridge as
provided but gated on that change.

## Connector operations, used directly

Joins, replica-aware repartitioning, and the other RDD operations are the connector's and are
used unchanged. Helenus ships no wrappers for them:

- `joinWithCassandraTable[R]` and `leftJoinWithCassandraTable[R]`
- `repartitionByCassandraReplica`
- `perPartitionLimit`
- `spanBy`

The read bridge composes with joins for free: `joinWithCassandraTable[R]` resolves the same
implicit `RowReaderFactory[R]`, so a Helenus factory bound in scope maps the joined rows
through Helenus codecs with no extra wrapper.

## Compatibility

The module pins one connector line to keep maintenance low, mirroring how `helenus-flink`
pins one Flink line. The recommended pin is connector 3.5.1 (the latest stable), which targets
Spark 3.5 and cross-publishes Scala 2.12 and 2.13.

| helenus-spark target | Spark | spark-cassandra-connector | Scala       | Connector's java driver | Min Java |
|----------------------|-------|---------------------------|-------------|-------------------------|----------|
| Recommended pin      | 3.5   | 3.5.1                     | 2.12, 2.13  | 4.18.1 (shaded)         | 11       |

- Spark 4.0 is not supported. The connector's compatibility matrix tops out at Spark 3.5;
  there is no Spark 4.x row yet. `helenus-spark` will not support Spark 4.0 until the connector
  does.
- Scala 2.13 support in the connector starts at connector 3.4, so the 2.13 cross-build requires
  connector 3.4 or later. The recommended 3.5.1 pin covers both 2.12 and 2.13.
- Min Java is 11, matching Helenus core. Spark 3.5 and the java driver themselves allow Java 8,
  but `helenus-spark` inherits core's JDK 11 baseline, so 11 is the floor.
- The connector 3.5.1 pins java driver 4.18.1 (shaded); Helenus core is on 4.19.3 (unshaded).
  The public `com.datastax.oss.driver.*` API is un-relocated in both, so `CqlSession` is the
  identical type, but two providers on one classpath must be reduced to one: the module
  converges on the connector's shaded driver on every classpath, so add only the connector at
  runtime, not a second unshaded `java-driver-core`.
- The connector has moved from DataStax to the ASF (`apache/cassandra-spark-connector`);
  coordinates and package names are unchanged today, and the module depends only on the
  connector's small, stable public SPI (`RowReaderFactory[T]`, `RowReader[T]`, and
  `CassandraConnector.withSessionDo`).
