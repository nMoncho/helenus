# helenus-akka-busl

Same API as [`helenus-akka`](../akka/README.md), but compiled against newer Akka released under
the Business Source License (BUSL 1.1). Qualifying production use of BUSL Akka may require a
commercial Lightbend license; review the Akka license before adopting this variant.

Usage is identical to `helenus-akka` (see its README). The examples are compile-checked here by
[`DocExamples.scala`](src/test/scala/net/nmoncho/helenus/akka/DocExamples.scala).

## Setup

```scala
// use the same version as helenus-core
libraryDependencies += "net.nmoncho" %% "helenus-akka-busl" % helenusVersion
```
