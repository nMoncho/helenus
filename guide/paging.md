# Paging

Queries return a `PagingIterable`, which fetches pages from Cassandra on demand. Helenus adds a few
Scala-friendly ways to consume it.


```scala
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.RowMapper

implicit val session: CqlSession = getSession
// session: CqlSession = com.datastax.oss.driver.internal.core.session.DefaultSession@6f67c7a8

case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)
case class Hotel(id: String, name: String, phone: String, address: Address, pois: Set[String])

implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()
// addressCodec: TypeCodec[Address] = UtdCodec[Address]
implicit val hotelMapper: RowMapper[Hotel]    = RowMapper[Hotel]()
// hotelMapper: RowMapper[Hotel] = net.nmoncho.helenus.internal.CaseClassRowMapperDerivation$$anonfun$net$nmoncho$helenus$internal$CaseClassRowMapperDerivation$$$nestedInanonfun$genericCCRowMapperBuilder$1$1@4432305f

val hotels = "SELECT * FROM hotels".toCQL.prepareUnit.as[Hotel]
// hotels: internal.cql.ScalaPreparedStatementUnit[Hotel] = net.nmoncho.helenus.internal.cql.ScalaPreparedStatementUnit@6331e9de
```

## Consuming results

```scala
// Materialize every row into a Scala collection:
hotels.execute().to(List)
// res0: List[Hotel] = List(
//   Hotel(
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

// Read only the first row:
hotels.execute().nextOption()
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

// Iterate lazily; the driver fetches the next page when the iterator needs it:
hotels.execute().iter.take(1).toList
// res2: List[Hotel] = List(
//   Hotel(
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

## Larger result sets

For large results, avoid `to(List)` (it pulls every page into memory). Prefer `iter` for lazy,
page-by-page iteration, or drive paging explicitly with `pager` and a `PagingState` when you need to
resume a query across requests (for example between web requests). The streaming integrations
(Akka, Pekko, Monix, ZIO, Flink) expose the same results as their native stream types; see each
module's README.

The [wiki](https://github.com/nMoncho/helenus/wiki) covers `PagingState` serialization and resuming
in more detail.
