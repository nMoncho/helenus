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


```scala
// First import helenus...
import net.nmoncho.helenus._

// Then convert your session and mark it implicit
implicit val session: CqlSessionExtension = getSession.toScala
// session: CqlSessionExtension = net.nmoncho.helenus.package$ClqSessionOps$$anon$1@578a371e

@Udt
case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.udtOf[Address]
// typeCodec: TypeCodec[Address] = UtdCodec[Address]

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]
// rowMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@7273abea

// We can prepare queries with parameters that don't require boxing
val hotelsById = "SELECT * FROM hotels WHERE id = ?".toCQL
    .prepare[String]
    .as[Hotel]
// hotelsById: internal.cql.ScalaPreparedStatement[String, Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatement@21be85bf

// We can extract a single result using `headOption`, or
// use `to(Coll)` to transform the result to a collection
hotelsById.execute("h1").headOption
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
```

For a more detailed guide on how to use Helenus, please read our [wiki](wiki). We also provides
[example projects](https://github.com/nMoncho/helenus-examples).