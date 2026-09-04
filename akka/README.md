# helenus-akka

Akka Streams / Alpakka Cassandra integration for Helenus: turn prepared statements into
`Source`s and `Sink`s. Compiled against Apache-licensed Akka 2.6.

> Note: this module uses Apache-licensed Akka. For newer (BUSL) Akka.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-akka" % helenusVersion
```

## Usage


```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.akka._

implicit val system: ActorSystem = ActorSystem("helenus-akka", cassandraConfig)
// system: ActorSystem = akka://helenus-akka

implicit val session: CassandraSession = CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())
// session: CassandraSession = akka.stream.alpakka.cassandra.scaladsl.CassandraSession@3fb84261

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults
// writeSettings: CassandraWriteSettings = CassandraWriteSettings(parallelism=1,maxBatchSize=100,maxBatchWait=500 milliseconds,batchType=LOGGED)

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val hotels: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels".toCQLAsync.prepareUnit.as[Hotel].asReadSource()
// hotels: Source[Hotel, NotUsed] = Source(SourceShape(FutureFlattenSource.out(716353842)))

val hotelById: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels WHERE id = ?".toCQLAsync.prepare[String].as[Hotel].asReadSource("h1")
// hotelById: Source[Hotel, NotUsed] = Source(SourceShape(FutureFlattenSource.out(1858572452)))

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[Hotel, Future[Done]] =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toCQLAsync
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .asWriteSink(writeSettings)
// insert: Sink[Hotel, Future[Done]] = Sink(SinkShape(FlatMapPrefix.in(704549564)))
```

