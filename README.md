<img align="left" width="64px" height="64px" src="docs/logo.svg"/>

# Helenus

---

![main status](https://github.com/nMoncho/helenus/actions/workflows/main.yaml/badge.svg)
[![Maven Central 2.13](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.nmoncho/helenus-core_2.13)

Helenus is collection of Scala utilities for Apache Cassandra. Its goal is to
make interacting with Cassandra easier, in a type-safe manner, while trying to
avoid introducing a complex API.

We also provide integration against several streaming libraries (each module has a short usage
guide in its own README):

- [Akka v2.6 (Apache License)](akka/README.md)
- [Akka BUSL](akka-busl/README.md)
- [Flink 2.x](flink/README.md)
- [Monix](monix/README.md)
- [Pekko](pekko/README.md)
- [ZIO](zio/README.md)

## Installation

Include the library into you project definition:

```scala
libraryDependencies += "net.nmoncho" %% "helenus-core" % "2.0.0-RC2+37-69ede86c+20260818-1004-SNAPSHOT"
```

The type-safe [Tables DSL](#tables-dsl) lives in its own module (currently published for Scala 2.13 only):

```scala
libraryDependencies += "net.nmoncho" %% "helenus-tables" % "2.0.0-RC2+37-69ede86c+20260818-1004-SNAPSHOT"
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
- CQL templating, with String Interpolation, validated at compile time. See [usage](#usage).
- `PreparedStatement`s and `BoundStatement`s extension methods.
- Short-hand imports and aliases available directly from `net.nmoncho.helenus._`.
- A new type-safe **Tables DSL**: describe a table as a case class and build `CREATE`, `DROP`, `SELECT`, `INSERT`, `UPDATE`, and `DELETE` statements with compile-time checks. See [Tables DSL](#tables-dsl).

### Supported Codecs

As of this version, Helenus supports the following types:

- Java types: `String`, `UUID`, `Instant`, `LocalDate`, `LocalTime`, `InetAddress`.
- `AnyVal` types: `Boolean`, `Byte`, `Double`, `Float`, `Int`, `Long`, `Short`.
  - This means, if used properly, no more boxing.
- Collections: `Seq`, `List`, `Vector`, `Map`, `Set`, `SortedMap`, `SortedSet`. See the
  [Codecs guide](guide/codecs.md).
- Enumerations: Can be encoded by name or by order. See the
  [Enumeration Codecs guide](guide/enumerations.md).
- Tuples: Encoded as regular Cassandra tuples.
- Case Classes: Encoded as regular Cassandra UDTs. See the [UDTs guide](guide/udts.md).
- Others: `Option`, and `Either` (encoded as a tuple).


## Usage


```scala
// First import helenus...
import net.nmoncho.helenus._

// Then mark your session implicit
implicit val session: CqlSession = getSession
// session: CqlSession = com.datastax.oss.driver.internal.core.session.DefaultSession@1b978517

case class Address(street: String, city: String, stateOrProvince: String, postalCode: String, country: String)

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

// We can derive Cassandra TypeCodecs used to map UDTs to case classes
implicit val typeCodec: TypeCodec[Address] = Codec.of[Address]()
// typeCodec: TypeCodec[Address] = UtdCodec[Address]

// We can derive how query results map to case classes
implicit val rowMapper: RowMapper[Hotel] = RowMapper[Hotel]()
// rowMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@2a86e8

val hotelId = "h1"
// hotelId: String = "h1"

// We can prepare queries with parameters that don't require boxing
val hotelsById = "SELECT * FROM hotels WHERE id = ?".toCQL
    .prepare[String]
    .as[Hotel]
// hotelsById: internal.cql.ScalaPreparedStatement1[String, Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatement1@b81693e

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
// interpolatedHotelsById: api.cql.WrappedBoundStatement[com.datastax.oss.driver.api.core.cql.Row] = net.nmoncho.helenus.api.cql.WrappedBoundStatement@3e8eba95

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

### Reusing derived `RowMapper`s

Deriving a `RowMapper` builds a nested mapper structure and computes the field-to-column
name transforms. This happens each time the mapper is materialized. Because `row.as[T]` and
`resultSet.as[T]` take the `RowMapper` as an implicit parameter, calling them in a hot loop
without a bound mapper re-runs the derivation on every row.

Bind the mapper to a single `implicit val` so it is derived once and reused. On the prepared
statement path (for example `prepare[String].as[Hotel]` above) this is already handled for you,
since the mapper is stored on the statement.

`RowMapper.of[T]` derives the mapper eagerly; `RowMapper.cached[T]` additionally memoizes it
process-wide (keyed by type and naming scheme), so even a repeated call never re-derives it:

```scala
// Prefer binding once as an `implicit val` in real code; shown as a plain val here to
// avoid introducing a second implicit `RowMapper[Hotel]` into this example's scope.
val reusableHotelMapper: RowMapper[Hotel] = RowMapper.cached[Hotel]
// reusableHotelMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@67fdb309
```

## Guides

These in-repo guides cover the core concepts and are compile-checked against the current version:

- [Codecs](guide/codecs.md): built-in, collection, `Option`/`Either` codecs.
- [Enumeration Codecs](guide/enumerations.md): encoding `Enumeration`s by name or by order.
- [UDTs](guide/udts.md): mapping case classes to UDTs, and the field-ordering rules.
- [Paging](guide/paging.md): consuming results and driving paging.

The [wiki](https://github.com/nMoncho/helenus/wiki) remains available as supplementary material.

## Tables DSL

The `helenus-tables` module adds a type-safe DSL built around a table described
as a case class. The case class is the single source of truth: DDL and full-row
projections derive every column from its fields, and a mismatch between the case
class and the declared columns fails to compile.

The DSL is imported from `net.nmoncho.helenus.tables`, matching how the streaming modules are
imported (the full set of public types is also available under `net.nmoncho.helenus.api.tables`):

```scala
import java.util.UUID

import net.nmoncho.helenus._
import net.nmoncho.helenus.tables._

case class User(id: UUID, username: String, age: Int, email: String)

object UsersTable extends Table[User]("docs", "users") {
  // Don't set explicit types on columns to keep full column tagging
  val id       = column[UUID]("id")
  val username = column[String]("username")
  val age      = column[Int]("age")
  val email    = column[String]("email")

  // Adding a field to `User` without a matching column fails to compile here
  protected val columns = registerAllColumns(id :: username :: age :: email :: HNil)

  // Type-level key declarations drive the DDL and the compile-time query gates
  type PK = id.Tag :: HNil
  type CK = username.Tag :: HNil
}
```

With the table in place you can build and run statements:

```scala
// CREATE TABLE
UsersTable.create.ifNotExists.execute()
// res2: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@14d33a96

val userId = UUID.fromString("b995a896-4ad8-471a-9b05-4fb6fbc6fdd6")
// userId: UUID = b995a896-4ad8-471a-9b05-4fb6fbc6fdd6

// INSERT: the whole primary key must be set, else it does not compile
UsersTable.insert
  .value(UsersTable.id := userId)
  .value(UsersTable.username := "alice")
  .value(UsersTable.age := 30)
  .execute()
// res3: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@b9e2559

// Or insert a whole entity at once
UsersTable.insertFrom(User(userId, "alice", 30, "alice@example.com")).execute()
// res4: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@70e61fb0

// SELECT returns a PagingIterable of the mapped case class
val users = UsersTable.select()
  .where(UsersTable.id === userId)
  .execute()
// users: com.datastax.oss.driver.api.core.PagingIterable[User] = com.datastax.oss.driver.internal.core.PagingIterableWrapper@1e6d608e

// UPDATE requires at least one assignment and a fully constrained primary key
UsersTable.update
  .set(UsersTable.age := 31)
  .where(UsersTable.id === userId and UsersTable.username === "alice")
  .execute()
// res5: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@def4a7d

// DELETE
UsersTable.delete
  .where(UsersTable.id === userId and UsersTable.username === "alice")
  .execute()
// res6: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@2a46ac10

// DROP TABLE
UsersTable.drop.ifExists.execute()
// res7: com.datastax.oss.driver.api.core.cql.ResultSet = com.datastax.oss.driver.internal.core.cql.SinglePageResultSet@46ec46a5
```

The DSL enforces at compile time what CQL enforces at runtime, so mistakes are
caught before you reach the database:

- `INSERT` and `UPDATE` require at least one assignment, and `INSERT` requires
  the whole partition and clustering key to be set.
- Queries that would otherwise need `ALLOW FILTERING` only compile when a
  suitable index is present, or when you opt in explicitly with `.allowFiltering`.
- Range and `IN` predicates are only allowed where CQL permits them, following
  the clustering-column prefix rules.

Passing the `?` bind marker instead of a value leaves a hole and produces a
`ScalaPreparedStatement` (or a function over the bound parameters) via `prepare`
and `prepareAsync`:

```scala
val byId = UsersTable.select().where(UsersTable.id === ?).prepare
// byId: internal.cql.ScalaPreparedStatement1[UUID, User] = net.nmoncho.helenus.api.tables.dml.ToPrepared$$anon$3$$anon$4@4f24ee28
```

The DSL also supports computed columns, [frozen](https://docs.datastax.com/en/cql-oss/3.3/cql/cql_reference/refCollectionTypes.html)
columns, secondary and custom (e.g. SAI) indices, named indices, and map key /
value / entry indices.

### Scope and roadmap

The Tables DSL covers `CREATE`, `DROP`, `SELECT`, `INSERT`, `UPDATE`, and `DELETE`,
with `USING TTL` / `USING TIMESTAMP`, `IF NOT EXISTS` on inserts, `IF EXISTS` on
updates and deletes, and the index features listed above.

The following are not implemented yet. Roughly in the order we expect to add them:

- **Static columns.** A column carries a `frozen` flag but no `static` flag.
- **Collection element operations.** `set(col := value)` and `set(col := ?)` are
  supported, but not element-level updates such as appending to a list, adding to
  or removing from a set or map, or setting a single map entry.
- **Counters.** No counter-increment assignment (`col = col + 1`).
- **LWT column conditions.** Conditional writes are limited to `IF EXISTS` /
  `IF NOT EXISTS`; per-column conditions (`IF col = value`) are not supported.
- **BATCH.** There is no batch builder for grouping several statements.

Static columns and collection element operations are the two most frequently
requested, so they are prioritized first.

Until a construct lands in the DSL, drop down to the CQL string API for it: build
the statement with the compile-time-checked `cql"..."` interpolator (or `toCQL`),
which lives alongside the DSL and interoperates with the same session and codecs.

## Migrating to v2

The v2 line introduces a few breaking changes:

- `ClqSessionOps` was renamed to `CqlSessionOps`.
- `ColumnNamingScheme`'s `map` method was renamed to `apply`, and its variants
  moved into the companion object.
- Deprecated UDT codec methods (including `udtOf`) were removed.
- Bound statements now go through a dedicated `ScalaBoundStatement` abstraction,
  and renamed-field handling is unified across `RowMapper` and `Mapping`. `RowMapper` now
  requires method application, if no renames are provided (eg. before you could get away with
  `RowMapper[A]`, now you need to do `RowMapper[A]()`).

For a more detailed guide on how to use Helenus, please read our [wiki](https://github.com/nMoncho/helenus/wiki). We also provide
[example projects](https://github.com/nMoncho/helenus-examples).
