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
// session: CassandraSession = akka.stream.alpakka.cassandra.scaladsl.CassandraSession@42a86e94

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults
// writeSettings: CassandraWriteSettings = CassandraWriteSettings(parallelism=1,maxBatchSize=100,maxBatchWait=500 milliseconds,batchType=LOGGED)

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val ices: Source[IceCream, NotUsed] =
  "SELECT * FROM ice_creams".toCQLAsync.prepareUnit.as[IceCream].asReadSource()
// ices: Source[IceCream, NotUsed] = Source(SourceShape(FutureFlattenSource.out(233149589)))

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[IceCream, Future[Done]] =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .asWriteSink(writeSettings)
// insert: Sink[IceCream, Future[Done]] = Sink(SinkShape(FlatMapPrefix.in(821296112)))
```

