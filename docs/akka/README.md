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

```scala mdoc:invisible
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.CassandraWriteSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

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

case class IceCream(name: String, numCherries: Int, cone: Boolean)
object IceCream {
  implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]()
  implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] = Adapter.builder[IceCream].build
}
```

```scala mdoc
import net.nmoncho.helenus._
import net.nmoncho.helenus.akka._

implicit val system: ActorSystem = ActorSystem("helenus-akka", cassandraConfig)

implicit val session: CassandraSession = CassandraSessionRegistry(system).sessionFor(CassandraSessionSettings())

val writeSettings: CassandraWriteSettings = CassandraWriteSettings.defaults

import system.dispatcher // or bring your own ExecutionContext

// A prepared SELECT becomes a Source:
val ices: Source[IceCream, NotUsed] =
  "SELECT * FROM ice_creams".toCQLAsync.prepareUnit.as[IceCream].asReadSource()

// A prepared INSERT becomes a Sink; each element supplies the bind parameters:
val insert: Sink[IceCream, Future[Done]] =
  "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toCQLAsync
    .prepare[String, Int, Boolean]
    .from[IceCream]
    .asWriteSink(writeSettings)
```

