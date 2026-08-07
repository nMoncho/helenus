# Helenus Benchmarks

## Running
To run the micro-benchmarks, use the script `run-benchmarks.sh`.

To run a specific benchmark (e.g. `ListCodecBenchMark`), use:
```bash
sbt 'bench/jmh:run -rf json .*ListCodecBenchMark'
```

## Rationale
The goal behind this micro-benchmarks is to get as close as possible to
Cassandra's performance. Each bench contains a baseline that can be used
to compare both measurements.

`MappingCodec`s are not measured since they require an extra allocation during
encoding/decoding.

## Coverage
Besides the primitive, collection, and tuple codecs, the suite covers the
higher-overhead paths:

- `UdtCodecBenchmark`: case class to UDT encode/decode, baselined against the
  DataStax `UdtValue` codec.
- `RowMapperBenchmark`: mapping a `Row` into a wide case class, comparing a
  hand-written mapper, a once-derived mapper reused across rows, and a mapper
  re-derived on every call.
- `MapOperatorBenchmark` / `TakeOperatorBenchmark`: the reactive `Map` and
  `Take` operators, baselined against consuming the source publisher directly.

All benchmarks are picked up automatically by `run-benchmarks.sh` (any
`*Benchmark.scala` under `bench/`).