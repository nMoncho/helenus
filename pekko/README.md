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
import net.nmoncho.helenus._

// A prepared SELECT becomes a Source:
val ices: Source[IceCream, NotUsed] =
  "SELECT * FROM ice_creams".toCQL.prepareUnit.as[IceCream].asReadSource()

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[IceCream, Future[Done]] =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .asWriteSink(writeSettings)
```

`toCQL`/`prepare` need an implicit `CqlSession`; `asReadSource`/`asWriteSink` need an implicit
Pekko `CassandraSession`. These snippets are compile-checked by
[`DocExamples.scala`](src/test/scala/net/nmoncho/helenus/pekko/DocExamples.scala).
