# helenus-monix

Monix integration for Helenus: turn prepared statements into `Observable`s and `Consumer`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-monix" % helenusVersion
```

## Usage

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import monix.reactive.Consumer
import monix.reactive.Observable

import net.nmoncho.helenus._

case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.of[Address]()

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()

implicit val rowAdapter: Adapter[Hotel, (String, String, String, Address, Set[String])] = Adapter.builder[Hotel].build

implicit val session: CqlSession = net.nmoncho.helenus.docs.DocsHelper.cqlSession
```

```scala mdoc
import net.nmoncho.helenus._
import net.nmoncho.helenus.monix._

// A prepared SELECT becomes an Observable:
val hotels: Observable[Hotel] =
  "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel].asObservable()

val hotelById: Observable[Hotel] =
  "SELECT * FROM hotels WHERE id = ?".toCQL.prepare[String].as[Hotel].asObservable("h1")

// A prepared INSERT becomes a Consumer; each element supplies the bind parameters:
val insert: Consumer[Hotel, Unit] =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toCQL
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .asConsumer()
```
