# User Defined Types (UDTs)

Helenus maps a case class to a Cassandra UDT and derives its codec from the case class fields.

```scala
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus._

case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)

implicit val addressCodec: TypeCodec[Address] = Codec.of[Address]()
// addressCodec: TypeCodec[Address] = UtdCodec[Address]

val encoded = addressCodec.encode(
  Address("Meent 78-82", "Rotterdam", "Zuid-Holland", "3011 JM", "Netherlands"),
  ProtocolVersion.DEFAULT
)
// encoded: java.nio.ByteBuffer = java.nio.HeapByteBuffer[pos=0 lim=70 cap=70]
val decoded = addressCodec.decode(encoded, ProtocolVersion.DEFAULT)
// decoded: Address = Address(
//   street = "Meent 78-82",
//   city = "Rotterdam",
//   stateOrProvince = "Zuid-Holland",
//   postalCode = "3011 JM",
//   country = "Netherlands"
// )
```

## Field ordering

`Codec.of[A]` maps case class fields to UDT fields **by position**, so the case class field order
must match the CQL type's column order. This is the recommended, fastest path.

When the codec is registered against a session whose UDT declares the fields in a different order,
`Codec.of[A]` adapts automatically by mapping on field **names** instead. For that name matching to
work, the case class field names must line up with the UDT column names once the
`ColumnNamingScheme` is applied.

## Naming scheme

Field names are converted to column names through an implicit `ColumnNamingScheme`. The default keeps
the name as-is; pass `SnakeCase` when your UDT uses snake_case columns (for example
`state_or_province`):

```scala
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.ColumnNamingScheme
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec

case class Address(
    street: String,
    city: String,
    stateOrProvince: String,
    postalCode: String,
    country: String
)

implicit val snake: ColumnNamingScheme = ColumnNamingScheme.SnakeCase
// snake: ColumnNamingScheme = net.nmoncho.helenus.api.ColumnNamingScheme$SnakeCase$@607dcc7a

val snakeCodec: TypeCodec[Address] = Codec.of[Address]()
// snakeCodec: TypeCodec[Address] = UtdCodec[Address]
```

For the full set of rules, including how to handle UDTs whose field order you already know differs,
see the [wiki UDTs guide](https://github.com/nMoncho/helenus/wiki/Codecs).
