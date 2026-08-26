# helenus-pekko

Apache Pekko Streams / Pekko Connectors Cassandra integration for Helenus: turn prepared
statements into `Source`s and `Sink`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-pekko" % helenusVersion
```

## Usage


```scala
import net.nmoncho.helenus.pekko._

implicit val system: ActorSystem = ActorSystem("helenus-pekko", cassandraConfig)
// system: ActorSystem = pekko://helenus-pekko

implicit val session: CassandraSession = CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())
// session: CassandraSession = org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession@5ef09001

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults
// writeSettings: CassandraWriteSettings = CassandraWriteSettings(parallelism=1,maxBatchSize=100,maxBatchWait=500 milliseconds,batchType=LOGGED)

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val hotels: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels".toCQLAsync.prepareUnit.as[Hotel].asReadSource()
// hotels: Source[Hotel, NotUsed] = Source(SourceShape(FutureFlattenSource.out(1493880607)))

val hotelById: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels WHERE id = ?".toCQLAsync.prepare[String].as[Hotel].asReadSource("h1")
// hotelById: Source[Hotel, NotUsed] = Source(SourceShape(FutureFlattenSource.out(2146822110)))

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[Hotel, Future[Done]] =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toCQLAsync
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .asWriteSink(writeSettings)
// insert: Sink[Hotel, Future[Done]] = Sink(SinkShape(FlatMapPrefix.in(933045601)))
```

