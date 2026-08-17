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
import net.nmoncho.helenus.flink.models.Address
import net.nmoncho.helenus.flink.models.Hotel
import net.nmoncho.helenus.flink.sink.CassandraSink
import net.nmoncho.helenus.flink.source.CassandraSource
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
```

```scala mdoc
import net.nmoncho.helenus._
import net.nmoncho.helenus.flink._

val env = StreamExecutionEnvironment.getExecutionEnvironment.setParallelism(2)

// Source: a prepared SELECT, given a function that builds it from a CqlSession.
val query = (session: CqlSession) =>
  "SELECT * FROM hotels".toCQL(session).prepareUnit.as[Hotel].apply()

val hotels: DataStream[Hotel] = env.fromSource(
  query.asSource(CassandraSource.Config()),
  WatermarkStrategy.noWatermarks(),
  "Cassandra Source"
)

// Sink: write a DataStream through a prepared INSERT (statement built per CqlSession).
rows.addCassandraSink(
  "INSERT INTO hotels(id, name, phone, address) VALUES (?, ?, ?, ?)".toCQL(_)
    .prepare[String, String, String, Address],
  CassandraSink.Config()
)
```

Populate `CassandraSource.Config()` / `CassandraSink.Config()` with your driver configuration.
These snippets are compile-checked by
[`DocExamples.scala`](src/test/scala/net/nmoncho/helenus/flink/DocExamples.scala).
