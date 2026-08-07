# helenus-zio

ZIO integration for Helenus: run queries in the `ZCqlSession` environment and turn prepared
statements into `ZStream`s and `ZSink`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-zio" % helenusVersion
```

## Usage

```scala
import net.nmoncho.helenus._

// A prepared SELECT becomes a ZStream over the ZCqlSession environment:
val ices: ZCqlStream[Try[IceCream]] =
  "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCream].stream()

// A prepared INSERT becomes a ZSink; each element supplies the bind parameters:
val insert =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .sink()
```

Note ZIO uses `to[T]` where the other modules use `as[T]`. The session is supplied through the
ZIO environment rather than an implicit. These snippets are compile-checked by
[`DocExamples.scala`](src/test/scala/net/nmoncho/helenus/zio/DocExamples.scala).
