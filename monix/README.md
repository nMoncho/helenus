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
import net.nmoncho.helenus.monix._

// A prepared SELECT becomes an Observable:
val hotels: Observable[Hotel] =
  "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel].asObservable()
// hotels: Observable[Hotel] = monix.reactive.internal.builders.ReactiveObservable@6ac190d8

val hotelById: Observable[Hotel] =
  "SELECT * FROM hotels WHERE id = ?".toCQL.prepare[String].as[Hotel].asObservable("h1")
// hotelById: Observable[Hotel] = monix.reactive.internal.builders.ReactiveObservable@726ccf4

// A prepared INSERT becomes a Consumer; each element supplies the bind parameters:
val insert: Consumer[Hotel, Unit] =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toCQL
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .asConsumer()
// insert: Consumer[Hotel, Unit] = <function1>
```
