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
libraryDependencies += "net.nmoncho" %% "helenus-core" % "0.6.0"
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
- Others: `Option`, and `Either` (encoded as a tuple).


## Usage

To begin using Helenus, import its package object `import net.nmoncho.helenus._`, and extend
your `CqlSession` with `.toScala`:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import net.nmoncho.helenus.docs._ // This import is not required, it's here to run MDoc // This import is not required, it's here to run MDoc

// Insert here your CqlSession
val cqlSession: CqlSession = DocsHelper.cqlSession
// cqlSession: CqlSession = com.datastax.oss.driver.internal.core.session.DefaultSession@1641843c

import net.nmoncho.helenus._
implicit val session: CqlSessionExtension = cqlSession.toScala
// session: CqlSessionExtension = net.nmoncho.helenus.package$ClqSessionOps$$anon$1@7a50402a
```

### Querying (CQL Templating)

You can query like you were using [String Interpolation](https://docs.scala-lang.org/overviews/core/string-interpolation.html):

```scala
val countryId = "nl"
// countryId: String = "nl"
val age = 18
// age: Int = 18

cql"SELECT * FROM population_by_country WHERE country = $countryId AND age > $age".execute()
// res0: ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@a08ae6c
```

An asychronous version is also available using `asyncCql`.

**NOTE**: CQL template doesn't support interpolating non-bound bound query arguments _yet_ (e.g. you can't interpolate
the table name `population_by_country`)

**NOTE 2**: This way of querying may be slow, improvements on the way!

### Querying (Extension method)

You can also query with some extension methods, treating queries as functions:

```scala
val query = "SELECT * FROM population_by_country WHERE country = ? AND age >= ?"
   .toCQL
   .prepare[String, Int]
// query: internal.cql.ScalaPreparedStatement[(String, Int), Row] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatement@553b8f38

// Notice there is no boxing required for `Int`
val olderThan18 = query("nl", 18).execute()
// olderThan18: ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@154e1832
val olderThan21 = query("nl", 21).execute()
// olderThan21: ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@33de2d84
```

The `prepare` method take as many type parameters as the query takes bound parameters. If the amount doesn't match, you
will get an exception from the DSE Java driver, as expected.

Every call to `query` returns a `BoundStatement` that can be executed at a later time. _Why don't we execute
the statement during application?_ We think this will lead to better integration with other libraries that use
statements, such as Alpakka.

After executing a query, you can obtain its result:

```scala
val rows = olderThan18.as[(String, Int, Int)].to(List)
// rows: List[(String, Int, Int)] = List(("nl", 18, 1000), ("nl", 21, 900))
```

If you don't require any integration, and want to do everything in less steps, it's also possible to use `as` in
the prepared statement:

```scala
"SELECT * FROM population_by_country WHERE country = ? AND age >= ?"
   .toCQL
   .prepare[String, Int]
   .as[(String, Int, Int)]
   .execute(countryId, age) // invoke goes here now!
   .to(List)
// res1: List[(String, Int, Int)] = List(("nl", 18, 1000), ("nl", 21, 900))
```

Notice that we don't provide query parameters to `apply` here, but use `execute` instead. We'd like to provide a unified
API for this, but a `BoundStatement` doesn't provide a way to map results before returning them to its client.

There is also an async flavour to this method with `executeAsync`:

```scala
"SELECT * FROM population_by_country WHERE country = ? AND age >= ?"
   .toCQL
   .prepare[String, Int]
   .as[(String, Int, Int)]
   .executeAsync(countryId, age) // invoke goes here now!
// res2: concurrent.Future[com.datastax.oss.driver.api.core.MappedAsyncPagingIterable[(String, Int, Int)]] = Future(Success(com.datastax.oss.driver.internal.core.AsyncPagingIterableWrapper@2ffad14c))
```

### Codecs

You can summon codecs with:

```scala
val anIntCodec = Codec[Int]
// anIntCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[Int] = net.nmoncho.helenus.internal.codec.IntCodec$@905ec23
val aTupleCodec = Codec[(String, Long)]
// aTupleCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[(String, Long)] = TupleCodec[(TEXT, BIGINT)]

// Either are encoded as tuples
val anEitherCodec = Codec[Either[String, java.util.UUID]] 
// anEitherCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[Either[String, java.util.UUID]] = net.nmoncho.helenus.internal.codec.EitherCodec@3b918f58
```

#### UDT Codecs

UDTs can be encoded as Case Classes, using the `@Udt` annotation. For a UDT defined as
`CREATE TYPE docs.ice_cream(name TEXT, num_cherries INT, cone BOOLEAN)` we can obtain its `TypeCodec` as:

```scala
import net.nmoncho.helenus.api.Udt

@Udt("docs", "ice_cream")
case class IceCream(name: String, numCherries: Int, cone: Boolean)

val iceCreamCodec = Codec.udtOf[IceCream]
// iceCreamCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[IceCream] = UtdCodec[IceCream]
```

This automatic derivation relies on the CQL type having the same field order as the case class (ie. in this case names
aren't  important,  just their types and order). If for some reason, you can't have the same order in both, consider using:

```scala
@Udt("docs", "ice_cream")
case class IceCreamShuffled(cone: Boolean, name: String, num_cherries: Int)

val iceCreamCodecShuffled = Codec.udtFrom[IceCreamShuffled](cqlSession)
// iceCreamCodecShuffled: com.datastax.oss.driver.api.core.type.codec.TypeCodec[IceCreamShuffled] = UtdCodec[IceCreamShuffled]
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

Enumerations can be encoded either by name, or by order, mapping the enumeration to a column of
type `TEXT`, or `INT` respectively.

Mark your enumeration either with the `NominalEncoded` or the `OrdinalEncoded` annotation:

```scala
import net.nmoncho.helenus.api.{ NominalEncoded, OrdinalEncoded }

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
// fingerCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[Fingers.Finger] = EnumerationNominalCodec[Fingers]
val planetCodec = Codec[Planets.Planet]
// planetCodec: com.datastax.oss.driver.api.core.type.codec.TypeCodec[Planets.Planet] = EnumerationOrdinalCodec[Planets]
```