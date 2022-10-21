# Helenus

![main status](https://github.com/nMoncho/helenus/actions/workflows/main.yaml/badge.svg)
[![Maven Central 2.13](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13)

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
val query = "SELECT * FROM population_by_country WHERE country = ? AND age >= ?"
   .toCQL
   .prepare[String, Int]

// Notice there is no boxing required for `Int`
val resultSet = query(countryId, age).execute()
```

Every call to invocation `query` returns a `BoundStatement` that can be executed at a later time. _Why don't we execute
the statement during application?_ We think that this will lead to better integration with other libraries that use
statements, like Alpakka.

After executing a query, you can obtain its result:

```scala mdoc
val rows = resultSet.as[(String, Int, Int)].to(List)
```

If you don't require any integration, and want to do everything in less steps, it's also possible to use the `as` in
the prepared statement:

```scala mdoc
"SELECT * FROM population_by_country WHERE country = ? AND age >= ?"
   .toCQL
   .prepare[String, Int]
   .as[(String, Int, Int)]
   .execute(countryId, age) // invoke goes here now!
   .to(List)
```

Notice that we don't provide query parameters to `apply` here, but use `execute` instead.


### Codecs

You can summon codecs with:

```scala mdoc
val anIntCodec = Codec[Int]
val aTupleCodec = Codec[(String, Long)]

// Either are encoded as tuples
val anEitherCodec = Codec[Either[String, java.util.UUID]] 
```

#### UDT Codecs

UDTs can be encoded as Case Classes, using the `@Udt` annotation. For a UDT defined as
`CREATE TYPE docs.ice_cream(name TEXT, num_cherries INT, cone BOOLEAN)` we can obtain its `TypeCodec` as:

```scala mdoc
import net.nmoncho.helenus.api.`type`.codec.Udt

@Udt("docs", "ice_cream")
case class IceCream(name: String, numCherries: Int, cone: Boolean)

val iceCreamCodec = Codec[IceCream] // or Codec.udtOf[IceCream]
```

This automatic derivation relies on the CQL type having the same field order as the case class (ie. in this case names
aren't  important,  just their types and order). If for some reason, you can't have the same order in both, consider using:

```scala mdoc
@Udt("docs", "ice_cream")
case class IceCreamShuffled(cone: Boolean, name: String, num_cherries: Int)

val iceCreamCodecShuffled = Codec.udtFrom[IceCreamShuffled](cqlSession)
```

In this case a `CqlSession` is used to fetch the CQL UDT definition.

Here names _are_ important, since they are used to find the order of a field in the CQL definition. If the case class
follows CamelCase naming, but the UDT follows SnakeCase, you can map these fields with `ColumnMapper`:

```scala
@Udt("docs", "ice_cream")
case class IceCreamShuffledCC(cone: Boolean, name: String, numCherries: Int)

import net.nmoncho.helenus.api.`type`.codec.{ ColumnMapper, SnakeCase }

implicit val snakeCase: ColumnMapper = SnakeCase
val iceCreamCodecShuffledCC = Codec.udtFrom[IceCreamShuffledCC](cqlSession)
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