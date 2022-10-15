# Helenus

![main status](https://github.com/nMoncho/helenus/actions/workflows/main.yaml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-code_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-code_2.13)

Helenus is collection of Scala utilities for Apache Cassandra. Its goal is to
make interacting with Cassandra easier, in a type-safe manner, while trying to
avoid introducing a complex API.

**Note**: The API is still experimental, and will probably stabilize in `v1.0.0`.

## Installation

Include the library into you project definition:

```scala
libraryDependencies += "net.nmoncho" %% "helenus-core" % "@VERSION@"
```

## Features

 - `TypeCodec`s for Scala types. All types extending `AnyVal`, Scala Collections, Scala `Enumeration`, etc.
   - Codecs for [UDTs](https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateUDT.html) defined as Case Classes.
   - Codecs for [Tuples](https://docs.datastax.com/en/cql-oss/3.3/cql/cql_using/useCreateTableTuple.html) defined with Scala Tuples.
 - CQL templating, with String Interpolation.
 - `PreparedStatement`s and `BoundStatement`s extension methods

### Supported Codecs

As of this version, Helenus supports the following types:

- Java types: `String`, `UUID`, `Instant`, `LocalDate`, `LocalTime`, `InetAddress`.
- `AnyVal` types: `Boolean`, `Byte`, `Double`, `Float`, `Int`, `Long`, `Short`.
  - This means, if used properly, no more boxing.
- Collections: `Seq`, `List`, `Vector`, `Map`, `Set`, `SortedMap`, `SortedSet`
- Enumerations: Can be encoded by name or by order. See [Enumeration Codecs](#enumeration-codecs).
- Tuples: Encoded as regular Cassandra tuples
- Case Classes: Encoded as regular Cassandra UDTs
- Others: `Option`, and `Either`.


## Usage

Import the necessary packages and classes. Also mark your as an implicit value `CqlSession`:

```scala mdoc
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.docs._ // This import is not required, it's here to run MDoc
import net.nmoncho.helenus._

// Insert here your CqlSession
val cqlSession: CqlSession = DocsHelper.cqlSession

implicit val session: CqlSessionExtension = cqlSession.toScala
```

### Querying (CQL Templating)

You can query Cassandra like you were using [String Interpolation](https://docs.scala-lang.org/overviews/core/string-interpolation.html):

```scala mdoc
import net.nmoncho.helenus._

val countryId = "nl"
val age = 18

cql"SELECT * FROM population_by_country WHERE country = $countryId AND age > $age".execute()
```

An asychronous version is also available using `asyncCql`.

**NOTE**: CQL template doesn't support interpolating non-bound bound query arguments _yet_ (e.g. you can't interpolate
the table name `population_by_country`)

### Querying (Extension method)

You can also query Cassandra with some extension methods, where we treat queries as functions:

```scala mdoc
val query = "SELECT * FROM population_by_country WHERE country = ? AND age = ?"
   .toCQL
   .prepare[String, Int]

// Notice there is no boxing required for `Int`
query(countryId, age).execute()
```

### Codecs

You can summon codecs with:

```scala mdoc
val anIntCodec = Codec[Int]
val aTupleCodec = Codec[(String, Long)]

// Either are encoded as tuples
val anEitherCodec = Codec[Either[String, java.util.UUID]] 
```

#### UDT Codecs

UDTs can be encoded as Case Classes, using the `@Udt` annotation:

```scala mdoc
import net.nmoncho.helenus.api.`type`.codec.Udt

@Udt("docs", "ice_cream")
case class IceCream(name: String, numCherries: Int, cone: Boolean)

val iceCreamCodec = Codec[IceCream] // or Codec.udtOf[IceCream]
```

### Enumeration Codecs

Enumerations can be encoded either by name, or by order. That means the mapping column
would be of type `TEXT`, or `INT` respectively.

Mark your enumeration with either `NominalEncoded` or `OrdinalEncoded`:

```scala mdoc
import net.nmoncho.helenus.api.`type`.codec._

@NominalEncoded
object Fingers extends Enumeration {
 type Finger = Value

 val Thumb, Index, Middle, Ring, Little = Value
}

@OrdinalEncoded
object Planets extends Enumeration {
 type Planet = Value

 val Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune = Value
}

val fingerCodec = Codec[Fingers.Finger]
val planetCodec = Codec[Planets.Planet]
```