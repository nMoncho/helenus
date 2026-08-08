# Enumeration Codecs

A Scala `Enumeration` can be stored either by name (nominal) or by position (ordinal).

```scala
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs

object Suit extends Enumeration {
  val Hearts, Diamonds, Clubs, Spades = Value
}

// Nominal: stored as the TEXT "Spades"
val byName: TypeCodec[Suit.Value] = TypeCodecs.enumerationNominalCodec(Suit)
// byName: TypeCodec[Suit.Value] = EnumerationNominalCodec[Suit]

// Ordinal: stored as the INT 3
val byOrder: TypeCodec[Suit.Value] = TypeCodecs.enumerationOrdinalCodec(Suit)
// byOrder: TypeCodec[Suit.Value] = EnumerationOrdinalCodec[Suit]

val roundTrip =
  byName.decode(byName.encode(Suit.Spades, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
// roundTrip: Suit.Value = Spades
```

Prefer the nominal codec when you might reorder or insert values later: ordinal encoding is
positional, so it shifts if the `Enumeration` changes. Nominal encoding is stable as long as the
value names are kept.

Both codecs can be made implicit and used like any other codec, including as the element codec of a
collection. See the [Codecs](codecs.md) guide for building collection and `Option`/`Either` codecs.
