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
// session: CassandraSession = org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession@29e7184a

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults
// writeSettings: CassandraWriteSettings = CassandraWriteSettings(parallelism=1,maxBatchSize=100,maxBatchWait=500 milliseconds,batchType=LOGGED)

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val ices: Source[IceCream, NotUsed] =
  "SELECT * FROM ice_creams".toCQLAsync.prepareUnit.as[IceCream].asReadSource()
// ices: Source[IceCream, NotUsed] = Source(SourceShape(FailedSource.out(52109298)))

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[IceCream, Future[Done]] =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .asWriteSink(writeSettings)
// insert: Sink[IceCream, Future[Done]] = Sink(SinkShape(FlatMapPrefix.in(1213143855)))
```

