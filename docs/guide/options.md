# Statement Options

Helenus lets you tune how a query runs — consistency level, page size, tracing, timeout,
idempotency, routing, execution profile — directly on a prepared statement, and it adds a few
helpers to the `CqlSession` itself. Everything here comes from `import net.nmoncho.helenus._`.

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession

def getSession: CqlSession = net.nmoncho.helenus.docs.DocsHelper.cqlSession

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus._

implicit val session: CqlSession = getSession
```

## Session helpers (`CqlSessionOps`)

Importing `net.nmoncho.helenus._` adds a handful of extension methods to `CqlSession`:

```scala mdoc
// Metadata for the connected keyspace, or for any keyspace by name:
session.sessionKeyspace.map(_.getName.asInternal())
session.keyspace("docs").map(_.getName.asInternal())

// An execution profile declared in the driver configuration (None if it does not exist):
session.executionProfile("default").isDefined
```

`registerCodecs` adds `TypeCodec`s to the session's registry. It is UDT-aware: a UDT codec is first
resolved against the connected keyspace, then registered.

```scala mdoc
case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)

implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()

session.registerCodecs(addressCodec)
```

## Options on a prepared statement (`Options`)

A `ScalaPreparedStatement` mixes in `Options`, so you tune it with fluent `with*` methods. The
options ride on the prepared statement and are applied to every bound statement it produces:

```scala mdoc
import com.datastax.oss.driver.api.core.ConsistencyLevel
import java.time.Duration
import net.nmoncho.helenus.api.RowMapper

case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

implicit val hotelMapper: RowMapper[Hotel] = RowMapper[Hotel]()

val hotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]

val tuned = hotels
  .withConsistencyLevel(ConsistencyLevel.LOCAL_ONE)
  .withPageSize(100)
  .withIdempotent(true)
  .withTimeout(Duration.ofSeconds(5))

tuned.execute().nextOption()
```

The full set of `with*` methods covers `withConsistencyLevel`, `withPageSize`, `withTracing`,
`withTimeout`, `withIdempotent`, `withExecutionProfile`, `withRoutingKeyspace`, `withRoutingKey`,
`withPagingState`, and `withIgnoreNullFields`. Each returns a new prepared statement, so they chain.

## Inspecting and reusing `StatementOptions`

The `with*` methods build up a `StatementOptions`, split into prepared-statement-level options
(`pstmtOptions`, currently just `ignoreNullFields`) and bound-statement-level options
(`bstmtOptions`: execution profile, routing, tracing, timeout, paging state, page size, consistency
level, and idempotency):

```scala mdoc
import net.nmoncho.helenus.api.cql.StatementOptions

// The defaults: everything falls back to the session's configuration.
StatementOptions.default

// The options carried by the statement we tuned above:
tuned.options
```

Build a `StatementOptions` yourself and apply it in one go with `withOptions`, for example to share
one option set across several statements:

```scala mdoc
val shared = StatementOptions.default.copy(
  bstmtOptions = StatementOptions.default.bstmtOptions.copy(
    consistencyLevel = Some(ConsistencyLevel.LOCAL_QUORUM),
    pageSize         = 500
  )
)

hotels.withOptions(shared).execute().nextOption()
```

## Ignoring null bind parameters

`ignoreNullFields` (on by default) skips `null` bind parameters when writing, so they become
*missing* values instead of explicit `null`s — avoiding a needless tombstone. Turn it off with
`withIgnoreNullFields(false)` when you actually want to write a `null`.

## Paging state

`withPagingState` sets where a query resumes; see the [Paging](paging.md) guide for driving paging
with a `PagingState`.
```

