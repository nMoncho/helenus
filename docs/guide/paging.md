# Paging

Queries return a `PagingIterable`, which fetches pages from Cassandra on demand. Helenus adds a few
Scala-friendly ways to consume it.

```scala mdoc:invisible
import com.datastax.oss.driver.api.core.CqlSession

def getSession: CqlSession = net.nmoncho.helenus.docs.DocsHelper.cqlSession
```

```scala mdoc
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.RowMapper

implicit val session: CqlSession = getSession

case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)
case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()
implicit val hotelMapper: RowMapper[Hotel]    = RowMapper[Hotel]()

val hotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
```

## Consuming results

```scala mdoc
// Materialize every row into a Scala collection:
hotels.execute().to(List)

// Read only the first row:
hotels.execute().nextOption()

// Iterate lazily; the driver fetches the next page when the iterator needs it:
hotels.execute().iter.take(1).toList
```

## Larger result sets

For large results, avoid `to(List)` (it pulls every page into memory). Prefer `iter` for lazy,
page-by-page iteration, or drive paging explicitly with `pager` and a `PagingState` when you need to
resume a query across requests (for example between web requests). The streaming integrations
(Akka, Pekko, Monix, ZIO, Flink) expose the same results as their native stream types; see each
module's README.

The [wiki](https://github.com/nMoncho/helenus/wiki) covers `PagingState` serialization and resuming
in more detail.
