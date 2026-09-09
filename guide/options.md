# Statement Options

Helenus lets you tune how a query runs — consistency level, page size, tracing, timeout,
idempotency, routing, execution profile — directly on a prepared statement, and it adds a few
helpers to the `CqlSession` itself. Everything here comes from `import net.nmoncho.helenus._`.


## Session helpers (`CqlSessionOps`)

Importing `net.nmoncho.helenus._` adds a handful of extension methods to `CqlSession`:

```scala
// Metadata for the connected keyspace, or for any keyspace by name:
session.sessionKeyspace.map(_.getName.asInternal())
// res0: Option[String] = Some(value = "docs")
session.keyspace("docs").map(_.getName.asInternal())
// res1: Option[String] = Some(value = "docs")

// An execution profile declared in the driver configuration (None if it does not exist):
session.executionProfile("default").isDefined
// res2: Boolean = true
```

`registerCodecs` adds `TypeCodec`s to the session's registry. It is UDT-aware: a UDT codec is first
resolved against the connected keyspace, then registered.

```scala
case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)

implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()
// addressCodec: TypeCodec[Address] = UtdCodec[Address]

session.registerCodecs(addressCodec)
// res3: util.Try[Unit] = Success(value = ())
```

## Options on a prepared statement (`Options`)

A `ScalaPreparedStatement` mixes in `Options`, so you tune it with fluent `with*` methods. The
options ride on the prepared statement and are applied to every bound statement it produces:

```scala
import com.datastax.oss.driver.api.core.ConsistencyLevel
import java.time.Duration
import net.nmoncho.helenus.api.RowMapper

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

implicit val hotelMapper: RowMapper[Hotel] = RowMapper[Hotel]()
// hotelMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@3b098bab

val hotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
// hotels: internal.cql.ScalaPreparedStatementUnit[Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatementUnit@369131e7

val tuned = hotels
  .withConsistencyLevel(ConsistencyLevel.LOCAL_ONE)
  .withPageSize(100)
  .withIdempotent(true)
  .withTimeout(Duration.ofSeconds(5))
// tuned: internal.cql.ScalaPreparedStatementUnit[Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatementUnit@58a5f19e

tuned.execute().nextOption()
// res4: Option[Hotel] = Some(
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

The full set of `with*` methods covers `withConsistencyLevel`, `withPageSize`, `withTracing`,
`withTimeout`, `withIdempotent`, `withExecutionProfile`, `withRoutingKeyspace`, `withRoutingKey`,
`withPagingState`, and `withIgnoreNullFields`. Each returns a new prepared statement, so they chain.

## Inspecting and reusing `StatementOptions`

The `with*` methods build up a `StatementOptions`, split into prepared-statement-level options
(`pstmtOptions`, currently just `ignoreNullFields`) and bound-statement-level options
(`bstmtOptions`: execution profile, routing, tracing, timeout, paging state, page size, consistency
level, and idempotency):

```scala
import net.nmoncho.helenus.api.cql.StatementOptions

// The defaults: everything falls back to the session's configuration.
StatementOptions.default
// res5: StatementOptions = StatementOptions(
//   pstmtOptions = PreparedStatementOptions(ignoreNullFields = true),
//   bstmtOptions = BoundStatementOptions(
//     profile = None,
//     routingKeyspace = None,
//     routingKey = None,
//     tracing = false,
//     timeout = None,
//     pagingState = None,
//     pageSize = 0,
//     consistencyLevel = None,
//     idempotent = None
//   )
// )

// The options carried by the statement we tuned above:
tuned.options
// res6: StatementOptions = StatementOptions(
//   pstmtOptions = PreparedStatementOptions(ignoreNullFields = true),
//   bstmtOptions = BoundStatementOptions(
//     profile = None,
//     routingKeyspace = None,
//     routingKey = None,
//     tracing = false,
//     timeout = Some(value = PT5S),
//     pagingState = None,
//     pageSize = 100,
//     consistencyLevel = Some(value = LOCAL_ONE),
//     idempotent = Some(value = true)
//   )
// )
```

Build a `StatementOptions` yourself and apply it in one go with `withOptions`, for example to share
one option set across several statements:

```scala
val shared = StatementOptions.default.copy(
  bstmtOptions = StatementOptions.default.bstmtOptions.copy(
    consistencyLevel = Some(ConsistencyLevel.LOCAL_QUORUM),
    pageSize         = 500
  )
)
// shared: StatementOptions = StatementOptions(
//   pstmtOptions = PreparedStatementOptions(ignoreNullFields = true),
//   bstmtOptions = BoundStatementOptions(
//     profile = None,
//     routingKeyspace = None,
//     routingKey = None,
//     tracing = false,
//     timeout = None,
//     pagingState = None,
//     pageSize = 500,
//     consistencyLevel = Some(value = LOCAL_QUORUM),
//     idempotent = None
//   )
// )

hotels.withOptions(shared).execute().nextOption()
// res7: Option[Hotel] = Some(
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

## Ignoring null bind parameters

`ignoreNullFields` (on by default) skips `null` bind parameters when writing, so they become
*missing* values instead of explicit `null`s — avoiding a needless tombstone. Turn it off with
`withIgnoreNullFields(false)` when you actually want to write a `null`.

## Paging state

`withPagingState` sets where a query resumes; see the [Paging](paging.md) guide for driving paging
with a `PagingState`.
```

