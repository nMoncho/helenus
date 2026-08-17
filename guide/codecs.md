# Codecs

Helenus maps Scala types to Cassandra columns through DataStax `TypeCodec`s. Most of the codecs you
need are provided out of the box and brought into scope with `import net.nmoncho.helenus._`. This
guide shows how to build codecs for the cases that are not automatic.


## Built-in codecs

Importing `net.nmoncho.helenus._` provides codecs for:

- Java types: `String`, `UUID`, `Instant`, `LocalDate`, `LocalTime`, `InetAddress`.
- `AnyVal` types: `Boolean`, `Byte`, `Double`, `Float`, `Int`, `Long`, `Short` (used properly, with
  no boxing).
- Common Scala collections: `Seq`, `List`, `Vector`, `Map`, `Set`, `SortedMap`, `SortedSet`.

## Collection codecs

Build a collection codec from an element codec with the helpers in `TypeCodecs`:

```scala
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs

val ints: TypeCodec[List[Int]] = TypeCodecs.listOf(TypeCodecs.intCodec)
// ints: TypeCodec[List[Int]] = ListCodec[INT]

val encoded = ints.encode(List(1, 2, 3), ProtocolVersion.DEFAULT)
// encoded: java.nio.ByteBuffer = java.nio.HeapByteBuffer[pos=0 lim=28 cap=28]
val decoded = ints.decode(encoded, ProtocolVersion.DEFAULT)
// decoded: List[Int] = List(1, 2, 3)
```

`seqOf`, `vectorOf`, `setOf`, `sortedSetOf`, and `mapOf` follow the same shape. Mutable variants are
available as `mutableSetOf`, `mutableMapOf`, and so on.

## Option and Either

`Option` maps CQL `NULL` to `None`. `Either` is encoded as a tuple of its two sides:

```scala
val maybeInt: TypeCodec[Option[Int]] = TypeCodecs.optionOf(TypeCodecs.intCodec)
// maybeInt: TypeCodec[Option[Int]] = OptionCodec[INT]

val intOrText: TypeCodec[Either[Int, String]] =
  TypeCodecs.eitherOf(TypeCodecs.intCodec, TypeCodecs.stringCodec)
// intOrText: TypeCodec[Either[Int, String]] = net.nmoncho.helenus.internal.codec.EitherCodec@3f2c94f5
```

## Case classes and tuples

A case class maps to a UDT and a Scala tuple maps to a CQL tuple. See the [UDTs](udts.md) guide for
field-ordering rules, and the [Enumeration Codecs](enumerations.md) guide for `Enumeration`s.

If you need a codec the library does not provide out of the box (for example a custom collection
type), the [wiki Codecs guide](https://github.com/nMoncho/helenus/wiki/Codecs) covers the extra
steps.
