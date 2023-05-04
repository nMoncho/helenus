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

