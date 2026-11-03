# User Defined Types (UDTs)

Helenus maps a case class to a Cassandra UDT and derives its codec from the case class fields.

```scala mdoc
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

val encoded = addressCodec.encode(
  Address("Meent 78-82", "Rotterdam", "Zuid-Holland", "3011 JM", "Netherlands"),
  ProtocolVersion.DEFAULT
)
val decoded = addressCodec.decode(encoded, ProtocolVersion.DEFAULT)
```

## Field ordering

`Codec.of[A]` maps case class fields to UDT fields **by position**, so the case class field order
must match the CQL type's column order. This is the recommended, fastest path.

When a statement is prepared with a `Codec.of[A]` codec bound directly to a UDT parameter, the codec
compares its fields with the UDT:

- Same field names (once the `ColumnNamingScheme` is applied) in a different order: the codec adapts
  by mapping on field **names** instead.
- Same field names in the same order: the codec is left as is.
- Field names that don't line up with the UDT: the codec stays position-based, and a warning is
  logged. The statement still works as long as the field types line up by position.

Adaptation only happens on that prepare path, so a codec needs the case class and UDT in the same
order in these cases:

- **Read-only statements**: nothing adapts a codec that's only used to read results, so decoding a
  reordered UDT fails.
- **UDTs wrapped in another codec**: for example `prepare[UUID, List[A]]` or
  `prepare[UUID, Option[A]]`, since the parameter's codec is the wrapper's. Binding a reordered UDT
  this way fails.

Use `Codec.nonIdenticalUdtOf[A]` or `Codec.udtFromFields[A]` when you know the order differs and one
of these applies.

## Naming scheme

Field names are converted to column names through an implicit `ColumnNamingScheme`. The default keeps
the name as-is; pass `SnakeCase` when your UDT uses snake_case columns (for example
`state_or_province`):

```scala mdoc:reset
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

val snakeCodec: TypeCodec[Address] = Codec.of[Address]()
```

For the full set of rules, including how to handle UDTs whose field order you already know differs,
see the [wiki UDTs guide](https://github.com/nMoncho/helenus/wiki/Codecs).
