# helenus-zio

ZIO integration for Helenus: run queries in the `ZCqlSession` environment and turn prepared
statements into `ZStream`s and `ZSink`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-zio" % helenusVersion
```

## Usage

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.zio._
import net.nmoncho.helenus.RowMapper
import net.nmoncho.helenus.Adapter
import scala.util.Try

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
// import net.nmoncho.helenus._ // Do not import! just use the following line
import net.nmoncho.helenus.zio._

// A prepared SELECT becomes a ZStream over the ZCqlSession environment:
val hotels: ZCqlStream[Try[Hotel]] =
  "SELECT * FROM hotels".toZCQL.prepareUnit.to[Hotel].stream()

val hotelById: ZCqlStream[Try[Hotel]] =
  "SELECT * FROM hotels WHERE id = ?".toZCQL.prepare[String].to[Hotel].stream("h1")

// A prepared INSERT becomes a ZSink; each element supplies the bind parameters:
val insert =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toZCQL
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .sink()
```

Note ZIO uses `to[T]` to map result rows, where the other modules use `as[T]`. This divergence is
intentional: ZIO effects already define an `as` method, so an `as[T]` enrichment would be shadowed
by it and could never be called. The session is supplied through the ZIO environment rather than an
implicit.
