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

The `CqlValidator` uses ANTLR4 to validate CQL queries. There are two `g4` files:

- `core/src/main/antlr4/CqlLexer.g4`
- `core/src/main/antlr4/CqlParser.64`

These are used to generate Java classes that ANTRL4 then uses to validate the queries:

- `core/src/main/java/net/nmoncho/helenus/internal/cql/CqlLexer.java`
- `core/src/main/java/net/nmoncho/helenus/internal/cql/CqlParser.java`

To generate these Java files we need to run ANTLR

```bash
$ curl -O https://www.antlr.org/download/antlr-4.13.2-complete.jar

$ java -jar antlr-4.13.2-complete.jar \
  -o ./core/src/main/java/net/nmoncho/helenus/internal/cql \        # output directory
  -package net.nmoncho.helenus.internal.cql \                       # Java package name
  -listener \                                                       # generate listener (default)
  -visitor \                                                        # generate visitor classes
  core/src/main/antlr4/CqlLexer.g4 core/src/main/antlr4/CqlParser.64
```