# helenus-monix

Monix integration for Helenus: turn prepared statements into `Observable`s and `Consumer`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-monix" % helenusVersion
```

## Usage

```scala
import net.nmoncho.helenus._

// A prepared SELECT becomes an Observable:
val ices: Observable[IceCream] =
  "SELECT * FROM ice_creams".toCQL.prepareUnit.as[IceCream].asObservable()

// A prepared INSERT becomes a Consumer; each element supplies the bind parameters:
val insert: Consumer[IceCream, Unit] =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQL
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .asConsumer()
```

Both need an implicit `CqlSession`. These snippets are compile-checked by
[`DocExamples.scala`](src/test/scala/net/nmoncho/helenus/monix/DocExamples.scala).
