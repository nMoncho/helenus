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


```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.flink._

val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)
// env: StreamExecutionEnvironment = org.apache.flink.streaming.api.environment.LocalStreamEnvironment@9afdec0

// Derive TypeInformation, or bring your own
import net.nmoncho.helenus.flink.typeinfo.TypeInformationDerivation._

implicit val addressTypeInformation: TypeInformation[Address] = Pojo[Address]
// addressTypeInformation: TypeInformation[Address] = PojoType<repl.MdocSession$MdocApp$Address, fields = [city: String, country: String, postalCode: String, stateOrProvince: String, street: String]>
implicit val hotelTypeInformation: TypeInformation[Hotel]     = Pojo[Hotel]
// hotelTypeInformation: TypeInformation[Hotel] = PojoType<repl.MdocSession$MdocApp$Hotel, fields = [address: PojoType<repl.MdocSession$MdocApp$Address, fields = [city: String, country: String, postalCode: String, stateOrProvince: String, street: String]>, id: String, name: String, phone: String, pois: Set[String]]>

// Source: a prepared SELECT, given a function that builds it from a CqlSession.
val query = (session: CqlSession) =>
  "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()
// query: CqlSession => ScalaBoundStatement[Hotel] = <function1>

val hotels: DataStream[Hotel] = env.fromSource(
  query.asSource(CassandraSource.Config()),
  WatermarkStrategy.noWatermarks(),
  "Cassandra Source"
)
// hotels: DataStream[Hotel] = org.apache.flink.streaming.api.datastream.DataStreamSource@73c8a6db

val rows: DataStream[(String, String, String, Address)] =
  hotels.map(new MapFunction[Hotel, (String, String, String, Address)] {
    override def map(h: Hotel): (String, String, String, Address) =
      (h.id, h.name, h.phone, h.address)
  })
// rows: DataStream[(String, String, String, Address)] = org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator@50ec9768

// Sink: write a DataStream through a prepared INSERT (statement built per CqlSession).
rows.addCassandraSink(
  "INSERT INTO hotels(id, name, phone, address) VALUES (?, ?, ?, ?)".toCQL(_)
    .prepare[String, String, String, Address],
  CassandraSink.Config()
)
// res0: CassandraSink[(String, String, String, Address)] = net.nmoncho.helenus.flink.sink.CassandraSink@56bee3d9
```

Populate `CassandraSource.Config()` / `CassandraSink.Config()` with your driver configuration.
