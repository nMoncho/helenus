# helenus-flink

Apache Flink integration for Helenus: use a prepared statement as a Flink `Source`, and write a
`DataStream` to Cassandra through a prepared statement.

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-flink" % helenusVersion
```

Flink and the DataStax driver are `provided`, so add them (and `flink-streaming-java`) alongside.

## Usage

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

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
import net.nmoncho.helenus._
import net.nmoncho.helenus.flink._

val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)

// Derive TypeInformation, or bring your own
import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._

implicit val addressTypeInformation: TypeInformation[Address] = Pojo[Address]
implicit val hotelTypeInformation: TypeInformation[Hotel]     = Pojo[Hotel]

// Source: a prepared SELECT, given a function that builds it from a CqlSession.
val query = (session: CqlSession) =>
  "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

val hotels: DataStream[Hotel] = env.fromSource(
  query.asSource(CassandraSource.Config()),
  WatermarkStrategy.noWatermarks(),
  "Cassandra Source"
)

val rows: DataStream[(String, String, String, Address)] =
  hotels.map(new MapFunction[Hotel, (String, String, String, Address)] {
    override def map(h: Hotel): (String, String, String, Address) =
      (h.id, h.name, h.phone, h.address)
  })

// Sink: write a DataStream through a prepared INSERT (statement built per CqlSession).
rows.addCassandraSink(
  "INSERT INTO hotels(id, name, phone, address) VALUES (?, ?, ?, ?)".toCQL(_)
    .prepare[String, String, String, Address],
  CassandraSink.Config()
)
```

Populate `CassandraSource.Config()` / `CassandraSink.Config()` with your driver configuration.
