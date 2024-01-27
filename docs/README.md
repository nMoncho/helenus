<img align="left" width="64px" height="64px" src="docs/logo.svg"/>

# Helenus

---

![main status](https://github.com/nMoncho/helenus/actions/workflows/main.yaml/badge.svg)
[![Maven Central 2.13](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13)

Helenus is collection of Scala utilities for Apache Cassandra. Its goal is to
make interacting with Cassandra easier, in a type-safe manner, while trying to
avoid introducing a complex API.

## Installation

Include the library into you project definition:

```scala
libraryDependencies += "net.nmoncho" %% "helenus-core" % "@VERSION@"
```

## Motivation

We tried using libraries such as [Phantom](https://outworkers.github.io/phantom/) and [Quill](https://github.com/zio/zio-quill),
which are great by the way, but they didn't fit entirely our mindset of workflow.
We believe the best way to use Cassandra,  or any DB for that matter, is to use its
Query Language directly.

Helenus takes inspiration from libraries such as [Anorm](https://github.com/playframework/anorm), trying to provide a
similar experience by putting CQL first. Our goals are:

- Give users control over the queries that are actually executed.
- Keep the library simple with a concise API.

## Features

 - `TypeCodec`s for Scala types. Every type extending `AnyVal`, most Scala Collections, Scala `Enumeration`, etc.
   - Codecs for [UDTs](https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateUDT.html) defined as Case Classes.
   - Codecs for [Tuples](https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateTableTuple.html) defined with Scala Tuples.
 - CQL templating, with String Interpolation. See [usage](#usage).
 - `PreparedStatement`s and `BoundStatement`s extension methods

### Supported Codecs

As of this version, Helenus supports the following types:

- Java types: `String`, `UUID`, `Instant`, `LocalDate`, `LocalTime`, `InetAddress`.
- `AnyVal` types: `Boolean`, `Byte`, `Double`, `Float`, `Int`, `Long`, `Short`.
  - This means, if used properly, no more boxing.
- Collections: `Seq`, `List`, `Vector`, `Map`, `Set`, `SortedMap`, `SortedSet`.
  - If you need a codec that isn't provided out of the box, please read [this guide](https://github.com/nMoncho/helenus/wiki/Codecs#where-is-the-typecodec-for-x-collection) on how to add it.
- Enumerations: Can be encoded by name or by order. See [Enumeration Codecs](#enumeration-codecs).
- Tuples: Encoded as regular Cassandra tuples
- Case Classes: Encoded as regular Cassandra UDTs
- Others: `Option`, and `Either` (encoded as a tuple).


## Usage

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.RowMapper

def getSession: CqlSession = net.nmoncho.helenus.docs.DocsHelper.cqlSession
```

```scala mdoc
// First import helenus...
import net.nmoncho.helenus._

// Then mark your session implicit
implicit val session: CqlSession = getSession

case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.udtOf[Address]()

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]

val hotelId = "h1"

// We can prepare queries with parameters that don't require boxing
val hotelsById = "SELECT * FROM hotels WHERE id = ?".toCQL
    .prepare[String]
    .as[Hotel]

// We can extract a single result using `nextOption()`, or
// use `to(Coll)` to transform the result to a collection
hotelsById.execute("h1").nextOption()

// We can also run the same using CQL interpolated queries
val interpolatedHotelsById = cql"SELECT * FROM hotels WHERE id = $hotelId"

interpolatedHotelsById.as[Hotel].execute().nextOption()
```

For a more detailed guide on how to use Helenus, please read our [wiki](https://github.com/nMoncho/helenus/wiki). We also provide
[example projects](https://github.com/nMoncho/helenus-examples).
