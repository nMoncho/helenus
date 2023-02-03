# Helenus Benchmarks

## Running
To run the micro-benchmarks, use the script `run-benchmarks.sh`.

To run a specific benchmark (e.g. `ListCodecBenchMark`), use:
```bash
sbt 'bench/jmh:run -rf json .*ListCodecBenchmark'
```

## Rationale
The goal behind this micro-benchmarks is to get as close as possible to
Cassandra's performance. Each bench contains a baseline that can be used
to compare both measurements.

`MappingCodec`s are not measured since they require an extra allocation during
encoding/decoding.
