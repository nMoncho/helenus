# helenus-migrations

Building blocks for Cassandra to Cassandra data migrations, following an ETL
approach: extract from a source table with a token-range full-table scan, transform
each row, and load into a target table.

The token-range planner lives in `net.nmoncho.helenus.migrations`. The per-backend
executors live in `net.nmoncho.helenus.migrations.pekko` and, for Flink, reuse
`net.nmoncho.helenus.flink`'s `CassandraSource`. A whole migration is one call,
`asTokenRangeMigration(...)`, which extracts, transforms, and loads with full
observability. Runnable example apps live under the `example` package.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-migrations" % helenusVersion
```

`helenus-migrations` is published for Scala 2.13 only. Every stream backend is a
`provided` dependency, so add the one you use (for example `helenus-pekko` plus the
Pekko connector, or `helenus-flink` plus Flink).

## Usage with Pekko

```scala mdoc:compile-only
object pekkoExample {
  import scala.concurrent.Future
  import scala.concurrent.duration._

  import com.datastax.oss.driver.api.core.CqlSession
  import com.datastax.oss.driver.api.core.metadata.token.Token
  import org.apache.pekko.Done
  import org.apache.pekko.actor.ActorSystem
  import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
  import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
  import org.apache.pekko.stream.scaladsl.{ Flow, Sink }

  import net.nmoncho.helenus._
  import net.nmoncho.helenus.pekko._
  import net.nmoncho.helenus.migrations._
  import net.nmoncho.helenus.migrations.pekko._

  final case class Hotel(id: String, name: String, city: String)
  object Hotel {
    implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()
  }

  final case class HotelByCity(city: String, id: String, name: String)
  object HotelByCity {
    implicit val adapter: Adapter[HotelByCity, (String, String, String)] = Adapter[HotelByCity]
  }

  // Migrate `hotels` (keyed by id) into `hotels_by_city` (keyed by city).
  def migrateHotels(
      implicit system: ActorSystem,
      session: CassandraSession,
      cql: CqlSession
  ): Future[Done] = {
    val plan = TokenRangePlanner.plan(splitsPerRange = 8)

    val load: Sink[HotelByCity, Future[Done]] =
      "INSERT INTO hotels_by_city (city, id, name) VALUES (?, ?, ?)".toCQL
        .prepare[String, String, String]
        .from[HotelByCity]
        .asWriteSink(CassandraWriteSettings.defaults)

    "SELECT id, name, city FROM hotels WHERE token(id) > ? AND token(id) <= ?".toCQL
      .prepare[Token, Token]
      .as[Hotel]
      .asTokenRangeMigration(
        plan,
        transform   = Flow[Hotel].map(hotel => HotelByCity(hotel.city, hotel.id, hotel.name)),
        sink        = load,
        parallelism = 4,
        rateLimit   = Some(RateLimit(1000, 1.second)),
        metrics     = MigrationMetrics.logging("hotels")
      )
      .run()
  }
}
```

## Usage with Flink

```scala mdoc:compile-only
object flinkExample {
  import com.datastax.oss.driver.api.core.CqlSession
  import com.datastax.oss.driver.api.core.cql.Row
  import org.apache.flink.api.common.typeinfo.TypeInformation
  import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

  import net.nmoncho.helenus._
  import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._
  import net.nmoncho.helenus.migrations.flink._

  // A no-arg constructor makes these Flink POJOs; the RowMapper is hand-written so it
  // captures no non-serializable codec objects (Flink serializes the source's mapper).
  final case class Hotel(id: String, name: String, city: String) {
    def this() = this("", "", "")
  }
  object Hotel {
    implicit val rowMapper: RowMapper[Hotel] = new RowMapper[Hotel] {
      override def apply(row: Row): Hotel =
        Hotel(row.getCol[String]("id"), row.getCol[String]("name"), row.getCol[String]("city"))
    }
    implicit val typeInfo: TypeInformation[Hotel] = Pojo[Hotel]
  }

  final case class HotelByCity(city: String, id: String, name: String) {
    def this() = this("", "", "")
  }
  object HotelByCity {
    implicit val adapter: Adapter[HotelByCity, (String, String, String)] = Adapter[HotelByCity]
    implicit val typeInfo: TypeInformation[HotelByCity]                  = Pojo[HotelByCity]
  }

  def migrateHotels(env: StreamExecutionEnvironment): Unit = {
    asTokenRangeMigration(
      env,
      read      = (s: CqlSession) =>
        "SELECT id, name, city FROM hotels".toCQL(s).prepareUnit.as[Hotel].apply(),
      transform = (hotel: Hotel) => HotelByCity(hotel.city, hotel.id, hotel.name),
      write     = (s: CqlSession) =>
        "INSERT INTO hotels_by_city (city, id, name) VALUES (?, ?, ?)".toCQL(s)
          .prepare[String, String, String]
          .from[HotelByCity]
    )

    env.execute()
  }
}
```

## Operations

### Rate limiting and backpressure

There is no default rate cap: a migration runs as fast as the cluster and stream
backpressure allow, and the reactive write sink already keeps the source from
outrunning the sink. Add a cap only to deliberately protect the source or target
cluster, by passing a `RateLimit(elements, per)`. Tune it against the target's spare
write capacity, not the client: keep the target healthy (p99 latency, pending
compactions) while the migration runs alongside production traffic, so start
conservative and raise it while watching the target. Because it is a single throttle
on a linear pipeline, it bounds end-to-end throughput, so the reads slow to match,
which also protects the source.

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

## Versioning

`helenus-migrations` ships in the next Helenus release, for Scala 2.13. It is a new
artifact, so binary-compatibility checking (MiMa) is enabled only after that first
release records a previous version to compare against.
