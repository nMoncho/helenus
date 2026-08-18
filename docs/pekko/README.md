# helenus-pekko

Apache Pekko Streams / Pekko Connectors Cassandra integration for Helenus: turn prepared
statements into `Source`s and `Sink`s.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-pekko" % helenusVersion
```

## Usage

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.cassandra.CassandraSessionSettings
import org.apache.pekko.stream.connectors.cassandra.CassandraWriteSettings
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSession
import org.apache.pekko.stream.connectors.cassandra.scaladsl.CassandraSessionRegistry
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

private def cassandraConfig: Config = ConfigFactory
  .parseString(s"""
                  |datastax-java-driver.basic {
                  |  contact-points = ["localhost:9142"]
                  |  session-keyspace = "docs"
                  |  load-balancing-policy.local-datacenter = "datacenter1"
                  |}""".stripMargin)
  .withFallback(ConfigFactory.load())

import net.nmoncho.helenus._

case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.of[Address]()

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()

implicit val rowAdapter: Adapter[Hotel, (String, String, String, Address, Set[String])] = Adapter.builder[Hotel].build
```

```scala mdoc
import net.nmoncho.helenus.pekko._

implicit val system: ActorSystem = ActorSystem("helenus-pekko", cassandraConfig)

implicit val session: CassandraSession = CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val hotels: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels".toCQLAsync.prepareUnit.as[Hotel].asReadSource()

val hotelById: Source[Hotel, NotUsed] =
  "SELECT * FROM hotels WHERE id = ?".toCQLAsync.prepare[String].as[Hotel].asReadSource("h1")

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[Hotel, Future[Done]] =
  "INSERT INTO hotels(id, name, phone, address, pois) VALUES(?, ?, ?, ?, ?)".toCQLAsync
    .prepare[String, String, String, Address, Set[String]]
    .from[Hotel]
    .asWriteSink(writeSettings)
```

