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
// import net.nmoncho.helenus._ // Do not import! just use the following line
import net.nmoncho.helenus.zio._

// A prepared SELECT becomes a ZStream over the ZCqlSession environment:
val hotels: ZCqlStream[Try[Hotel]] =
  "SELECT * FROM hotels".toZCQL.prepareUnit.to[Hotel].stream()
// hotels: ZCqlStream[Try[Hotel]] = zio.stream.ZStream@20b27923

val hotelById: ZCqlStream[Try[Hotel]] =
  "SELECT * FROM hotels WHERE id = ?".toZCQL.prepare[String].to[Hotel].stream("h1")
// hotelById: ZCqlStream[Try[Hotel]] = zio.stream.ZStream@7e3faee0

// A prepared INSERT becomes a ZSink; each element supplies the bind parameters:
val insert =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toZCQL
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .sink()
// insert: zio.stream.ZSink[ZCqlSession, Throwable, Hotel, Nothing, Unit] = zio.stream.ZSink@898c80ae
```

Note ZIO uses `to[T]` to map result rows, where the other modules use `as[T]`. This divergence is
intentional: ZIO effects already define an `as` method, so an `as[T]` enrichment would be shadowed
by it and could never be called. The session is supplied through the ZIO environment rather than an
implicit.
