# Developer Notes

This document includes information that doesn't fit in the code.

## Codecs

### UDT Codecs - Field Order

Datastax Java Driver encodes UDTs sequentially by fields, in the same way as tuples or
collections are encoded (ie. one element after the other). There is no notion of "field names".
This means that the first and second types are encoded differently, but the first and the third
are encoded identically, even if they are different types.

```cql
CREATE TYPE foo(name TEXT, age  INT);
CREATE TYPE bar(age  INT,  name TEXT);
CREATE TYPE taz(address TEXT, number  INT);
```

This also means that when creating a `TypeCodec`, the `UserDefinedType` users need
to provide has to align with the `CQL TYPE` as defined in the database.

## Compile-Time CQL Queries and ANTLR4

The `CqlValidator` uses ANTLR4 to validate CQL queries. The grammar lives in two `g4` files:

- `core/src/main/antlr4/CqlLexer.g4`
- `core/src/main/antlr4/CqlParser.g4`

The lexer and parser Java classes are generated **at build time** by the
[`sbt-antlr4`](https://github.com/ihji/sbt-antlr4) plugin (configured on the `core`
module in `build.sbt`), so nothing generated is committed and the sources can never
drift from the grammar or the ANTLR runtime version. To regenerate after editing a
grammar, just build:

```bash
$ sbt core/compile
```

The generated sources land under `core/target/.../src_managed/main/antlr4/`. Only the
lexer and parser are produced (`antlr4GenListener`/`antlr4GenVisitor` are off) because
`CqlValidator` uses only those; the ANTLR tool version is pinned to
`Dependencies.Version.antlr4`, the same version as the `antlr4-runtime` dependency.

### CQL String Interpolation - Bind Markers vs Injected Text

An interpolated parameter is either bound, or injected into the query text as is. Which one
applies is not decided by what the parameter is, but by where it sits:

```scala
// `tableName` and `name` are injected, `DefaultName` is bound
cql"SELECT * FROM $tableName WHERE $name = $DefaultName ALLOW FILTERING"
```

`CqlQueryInterpolation` asks the grammar. Every parameter starts out as a bind marker, and the
statement is handed to `CqlValidator` as a whole; whenever the parser rejects a marker, that
parameter is injected into the query text instead and the statement is checked again. Each round
settles one parameter, so this converges in at most one round per parameter.

Two things follow from asking the grammar rather than looking at the value:

- A compile-time constant in a value position is *bound*, not spliced into the query text, so it
  goes through its `TypeCodec` like any other value. That means no CQL literal quoting or
  escaping to get wrong. Only positions where CQL has no room for a bind marker (a table name, a
  column name, `LIMIT`) get the constant injected, and only a constant can go there at all.
- Since the compiler folds constants into literals before the macro runs, a bound constant has no
  name left to build a marker out of, so one is derived from its position (`:p2`). Parameters that
  do have a name keep it (`:nickname`).

A parameter the parser rejects that *isn't* a compile-time constant can't be injected, so the
statement stays invalid and `CqlValidator` reports it. In that case the error shows the statement
with every constant injected, the way the caller wrote it, so it points at the query they typed
rather than at bind markers they never asked for.

## Test coverage

Coverage is produced with [sbt-scoverage](https://github.com/scoverage/sbt-scoverage) through the
`testCoverage` alias, which CI runs on JDK 11 / Scala 2.13:

```bash
$ sbt testCoverage
```

**What is enforced.** The 70% statement- and branch-coverage floor (`coverageFailOnMinimum`) is
applied to `core` only.  To start gating another module once its baseline is known, add
`.settings(enforcedCoverage)` to it.