# Enumeratum Codecs

Besides Scala's built-in `Enumeration` (see the [Enumeration Codecs](enumerations.md) guide),
Helenus can map an [Enumeratum](https://github.com/lloydmeta/enumeratum) `Enum` to a CQL column,
either by name (nominal) or by position (ordinal).

Enumeratum is a `Provided` dependency, so add it to your build to use these codecs:

```scala
libraryDependencies += "com.beachape" %% "enumeratum" % "<version>"
```

Because Enumeratum is optional, its codecs live in a dedicated `EnumeratumCodecs` object rather than
in the always-imported `net.nmoncho.helenus._` scope. Import it explicitly where you need it:

```scala mdoc
import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import enumeratum._
import net.nmoncho.helenus.api.`type`.codec.EnumeratumCodecs

sealed trait Suit extends EnumEntry
object Suit extends Enum[Suit] {
  val values = findValues

  case object Hearts extends Suit
  case object Diamonds extends Suit
  case object Clubs extends Suit
  case object Spades extends Suit
}

// Nominal: stored as the TEXT "Spades" (the entry name)
val byName: TypeCodec[Suit] = EnumeratumCodecs.enumeratumNominalCodec(Suit)

// Ordinal: stored as the INT 3 (the position in `values`)
val byOrder: TypeCodec[Suit] = EnumeratumCodecs.enumeratumOrdinalCodec(Suit)

val roundTrip =
  byName.decode(byName.encode(Suit.Spades, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)
```

Prefer the nominal codec when you might reorder or insert values later: ordinal encoding is
positional, so it shifts if the entries are reordered. Nominal encoding is stable as long as the
`entryName`s are kept.

## Implicit derivation

An Enumeratum codec can also be summoned implicitly. Annotate the **sealed trait** (the `EnumEntry`
subtype, not the `Enum` object) with either `@NominalEncoded` or `@OrdinalEncoded` to pick the
representation, make the `Enum` available as an implicit, and import `EnumeratumCodecs._`:

> Note: this is the opposite of the `Enumeration` codecs, where the annotation goes on the `object`
> (because there the type parameter is the object's singleton type). For Enumeratum the codec's type
> parameter is the `EnumEntry` subtype, so the annotation must sit on the sealed trait.

```scala mdoc
import net.nmoncho.helenus._
import net.nmoncho.helenus.api.`type`.codec.EnumeratumCodecs._
import net.nmoncho.helenus.api.NominalEncoded

@NominalEncoded
sealed trait Priority extends EnumEntry
object Priority extends Enum[Priority] {
  val values = findValues

  case object Low extends Priority
  case object High extends Priority

  implicit val priorityEnum: Enum[Priority] = this
}

// Summoned thanks to the `@NominalEncoded` marker and the implicit `Enum`
val priorityCodec: TypeCodec[Priority] = Codec[Priority]
```

Both codecs can be used like any other codec, including as the element codec of a collection. See
the [Codecs](codecs.md) guide for building collection and `Option`/`Either` codecs.
