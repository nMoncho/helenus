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
libraryDependencies += "net.nmoncho" %% "helenus-core" % "1.2.0"
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


```scala
// First import helenus...
import net.nmoncho.helenus._

// Then mark your session implicit
implicit val session: CqlSession = getSession
// session: CqlSession = com.datastax.oss.driver.internal.core.session.DefaultSession@7180d451

case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.udtOf[Address]()
// typeCodec: TypeCodec[Address] = UtdCodec[Address]

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]
// rowMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@2aa1c6ab

val hotelId = "h1"
// hotelId: String = "h1"

// We can prepare queries with parameters that don't require boxing
val hotelsById = "SELECT * FROM hotels WHERE id = ?".toCQL
    .prepare[String]
    .as[Hotel]
// hotelsById: internal.cql.ScalaPreparedStatement1[String, Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatement1@4d51eb9c

// We can extract a single result using `nextOption()`, or
// use `to(Coll)` to transform the result to a collection
hotelsById.execute("h1").nextOption()
// res0: Option[Hotel] = Some(
//   value = Hotel(
//     id = "h1",
//     name = "The New York Hotel Rotterdam",
//     phone = "+31 10 217 3000",
//     address = Address(
//       street = "Meent 78-82",
//       city = "Rotterdam",
//       stateOrProvince = "Zuid-Holland",
//       postalCode = "3011 JM",
//       country = "Netherlands"
//     ),
//     pois = Set("Erasmus Bridge", "Markthal Rotterdam", "Rotterdam Zoo")
//   )
// )

// We can also run the same using CQL interpolated queries
val interpolatedHotelsById = cql"SELECT * FROM hotels WHERE id = $hotelId"
// interpolatedHotelsById: api.cql.WrappedBoundStatement[com.datastax.oss.driver.api.core.cql.Row] = net.nmoncho.helenus.api.cql.WrappedBoundStatement@5b5da04d

interpolatedHotelsById.as[Hotel].execute().nextOption()
// res1: Option[Hotel] = Some(
//   value = Hotel(
//     id = "h1",
//     name = "The New York Hotel Rotterdam",
//     phone = "+31 10 217 3000",
//     address = Address(
//       street = "Meent 78-82",
//       city = "Rotterdam",
//       stateOrProvince = "Zuid-Holland",
//       postalCode = "3011 JM",
//       country = "Netherlands"
//     ),
//     pois = Set("Erasmus Bridge", "Markthal Rotterdam", "Rotterdam Zoo")
//   )
// )
```

For a more detailed guide on how to use Helenus, please read our [wiki](https://github.com/nMoncho/helenus/wiki). We also provide
[example projects](https://github.com/nMoncho/helenus-examples).