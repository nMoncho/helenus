/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

/** The shared conformance corpus.
  *
  * A broad, data-driven list of '''valid''' CQL, grouped by statement type. It is consumed by two
  * specs:
  *
  *   - [[CqlConformanceSpec]] (C1) asserts [[CqlValidator]] accepts every entry.
  *   - [[CqlDifferentialSpec]] (C2) asserts the validator's accept/reject decision never disagrees
  *     with a real Cassandra in the harmful direction (validator rejects what Cassandra accepts).
  *
  * The bias is deliberate: this list only holds CQL that Cassandra accepts, because rejecting valid
  * CQL is the harmful failure mode (an unbypassable compile error on the `cql"..."` / `toCQL` path).
  * Keep it easy to grow: add a string to the relevant group.
  *
  * The corpus is derived from the Apache Cassandra 5.0 CQL reference (the statement/clause/type
  * surface documented there) and cross-checked against the pinned grammar in
  * `tools/antlr-import/`. One intentional deviation from Cassandra is honoured here: bind markers
  * (`?` / `:name`) are '''not''' valid as bare `SELECT` selectors (see the antlr-import README), so
  * no such entry appears below.
  */
object CqlConformanceCorpus {

  /** Every group as `(description, queries)`. Flatten with [[queries]]. */
  val groups: Seq[(String, Seq[String])] = Seq(
    "SELECT selectors" -> Seq(
      "SELECT * FROM t",
      "SELECT id FROM t",
      "SELECT id, name FROM t",
      "SELECT id, name, email FROM t",
      "SELECT ks.t.id FROM ks.t",
      "SELECT t.id FROM t",
      "SELECT id AS pk FROM t",
      "SELECT id AS pk, name AS n FROM t",
      "SELECT DISTINCT id FROM t",
      "SELECT DISTINCT a, b FROM t",
      "SELECT JSON id FROM t",
      "SELECT JSON * FROM t",
      "SELECT JSON id, name FROM t",
      "SELECT COUNT(*) FROM t",
      "SELECT COUNT(1) FROM t",
      "SELECT count(*) AS total FROM t",
      "SELECT MIN(v), MAX(v), SUM(v), AVG(v) FROM t",
      "SELECT WRITETIME(name) FROM t",
      "SELECT TTL(name) FROM t",
      "SELECT WRITETIME(name), TTL(name) FROM t",
      "SELECT MAXWRITETIME(name) FROM t",
      "SELECT CAST(count AS INT) FROM t",
      "SELECT CAST(count AS DOUBLE) FROM t",
      "SELECT avg(CAST(count AS DOUBLE)) FROM t",
      "SELECT toJson(id) FROM t",
      "SELECT id, toTimestamp(now()) FROM t",
      "SELECT m['k'] FROM t",
      "SELECT l[0] FROM t",
      "SELECT m['a'], m['b'] FROM t"
    ),
    // keyword-named function calls (token/count/writetime/ttl) and bind markers as
    // function arguments. Closed by the Cassandra 5.0 grammar migration; pinned here so a future
    // re-import that drops them turns red.
    "A1 keyword-named function calls and bind-marker arguments" -> Seq(
      "SELECT token(id) FROM t",
      "SELECT token(a, b) FROM t",
      "SELECT id FROM t WHERE token(id) > token(?)",
      "SELECT id FROM t WHERE token(id) = token(1)",
      "SELECT id FROM t WHERE token(id) >= token(:pk)",
      "SELECT id FROM t WHERE token(a, b) <= token(?, ?)",
      "SELECT WRITETIME(name) FROM t",
      "SELECT TTL(name) FROM t",
      "SELECT MAXWRITETIME(name) FROM t",
      "SELECT id FROM t WHERE ts >= minTimeuuid(?)",
      "SELECT id FROM t WHERE ts <= maxTimeuuid(:until)"
    ),
    "SELECT keyword-named columns" -> Seq(
      "SELECT date, text, int, timestamp FROM t",
      "SELECT type FROM t",
      "SELECT key FROM t",
      "SELECT keyspace_name FROM system_schema.keyspaces",
      "SELECT ttl FROM t",
      "SELECT count FROM t"
    ),
    "SELECT WHERE relations" -> Seq(
      "SELECT id FROM t WHERE id = 1",
      "SELECT id FROM t WHERE id = ?",
      "SELECT id FROM t WHERE id = :id",
      "SELECT id FROM t WHERE id = :USER_ID",
      "SELECT id FROM t WHERE partition_key = :partition_key",
      "SELECT id FROM t WHERE id = ? AND name = :name",
      "SELECT id FROM t WHERE a = ? AND b = ? AND c = ?",
      "SELECT id FROM t WHERE n = -1",
      "SELECT id FROM t WHERE n > 0",
      "SELECT id FROM t WHERE n >= 0 AND n <= 10",
      "SELECT id FROM t WHERE n < 100",
      "SELECT id FROM t WHERE n != 5",
      "SELECT id FROM t WHERE id IN (1, 2, 3)",
      "SELECT id FROM t WHERE id IN (?, ?, ?)",
      "SELECT id FROM t WHERE id IN ?",
      "SELECT id FROM t WHERE (a, b) IN ((1, 2), (3, 4))",
      "SELECT id FROM t WHERE (a, b) > (1, 2)",
      "SELECT id FROM t WHERE token(id) > token(?)",
      "SELECT id FROM t WHERE token(a, b) >= token(1, 2)",
      "SELECT id FROM t WHERE name CONTAINS 'x'",
      "SELECT id FROM t WHERE tags CONTAINS KEY 'k'",
      "SELECT id FROM t WHERE m['k'] = 'v'",
      "SELECT id FROM t WHERE name LIKE 'a%'",
      "SELECT id FROM t WHERE name LIKE ?",
      "SELECT id FROM t WHERE id = ? AND name LIKE 'jo%'",
      "SELECT id FROM t WHERE tags CONTAINS 'x' ALLOW FILTERING",
      "SELECT id FROM t WHERE m CONTAINS KEY 'k' ALLOW FILTERING"
    ),
    "SELECT clauses and modifiers" -> Seq(
      "SELECT id FROM t GROUP BY id",
      "SELECT id, count(*) FROM t GROUP BY id",
      "SELECT id, name, count(*) FROM t GROUP BY id, name",
      "SELECT id FROM t ORDER BY name ASC",
      "SELECT id FROM t ORDER BY name DESC",
      "SELECT id FROM t ORDER BY a ASC, b DESC",
      "SELECT id FROM t LIMIT 10",
      "SELECT id FROM t LIMIT ?",
      "SELECT id FROM t PER PARTITION LIMIT 10",
      "SELECT id FROM t PER PARTITION LIMIT 10 LIMIT 100",
      "SELECT id FROM t WHERE id = ? ALLOW FILTERING",
      "SELECT id FROM t GROUP BY id ORDER BY name DESC PER PARTITION LIMIT 1 LIMIT 10 ALLOW FILTERING",
      "SELECT id FROM t WHERE id = ? AND name = ? ALLOW FILTERING"
    ),
    "INSERT statements" -> Seq(
      "INSERT INTO t (id, name) VALUES (?, ?)",
      "INSERT INTO t (id, name) VALUES (:id, :name)",
      "INSERT INTO ks.t (id, name) VALUES (1, 'a')",
      "INSERT INTO t (id) VALUES (?)",
      "INSERT INTO t (id, name) VALUES (?, ?) IF NOT EXISTS",
      "INSERT INTO t (id) VALUES (?) USING TTL 86400",
      "INSERT INTO t (id) VALUES (?) USING TIMESTAMP 123",
      "INSERT INTO t (id, name) VALUES (?, ?) USING TTL 86400 AND TIMESTAMP 1000",
      "INSERT INTO t (id) VALUES (?) IF NOT EXISTS USING TTL 60",
      "INSERT INTO t (id, date, text) VALUES (?, ?, ?)",
      "INSERT INTO t JSON '{\"id\": 1}'",
      "INSERT INTO t JSON '{\"id\": 1}' DEFAULT UNSET",
      "INSERT INTO t JSON '{\"id\": 1}' DEFAULT NULL",
      "INSERT INTO t JSON ?",
      "INSERT INTO t (id, tags) VALUES (1, {'a', 'b'})",
      "INSERT INTO t (id, m) VALUES (1, {'a': 1, 'b': 2})",
      "INSERT INTO t (id, l) VALUES (1, [1, 2, 3])",
      "INSERT INTO t (id, tup) VALUES (?, (1, 'a'))",
      "INSERT INTO t (id, ts) VALUES (?, now())",
      "INSERT INTO t (id, u) VALUES (?, uuid())",
      "INSERT INTO t (id, ts) VALUES (?, toTimestamp(now()))",
      "INSERT INTO t (id, v) VALUES (1, null)",
      "INSERT INTO t (id, name) VALUES (1, 'Alice')"
    ),
    "UPDATE statements" -> Seq(
      "UPDATE t SET name = ? WHERE id = ?",
      "UPDATE t SET name = :name WHERE id = :id",
      "UPDATE t SET name = 'Bob' WHERE id = 1",
      "UPDATE t SET name = ?, email = ? WHERE id = ?",
      "UPDATE t SET date = ?, text = ?, int = ? WHERE id = ?",
      "UPDATE t USING TTL 400 SET name = ? WHERE id = ?",
      "UPDATE t USING TIMESTAMP 1000 SET name = ? WHERE id = ?",
      "UPDATE t USING TTL 400 AND TIMESTAMP 1000 SET name = ? WHERE id = ?",
      "UPDATE t SET counter = counter + 1 WHERE id = ?",
      "UPDATE t SET counter = counter - 1 WHERE id = ?",
      "UPDATE t SET l = l + [1] WHERE id = ?",
      "UPDATE t SET l = l - [1] WHERE id = ?",
      "UPDATE t SET l = [1] + l WHERE id = ?",
      "UPDATE t SET m = m + {'a': 1} WHERE id = ?",
      "UPDATE t SET s = s + {'a'} WHERE id = ?",
      "UPDATE t SET s = s - {'a'} WHERE id = ?",
      "UPDATE t SET m['k'] = 'v' WHERE id = ?",
      "UPDATE t SET l[0] = 'v' WHERE id = ?",
      "UPDATE t SET name = ? WHERE id IN (1, 2, 3)",
      "UPDATE t SET name = ? WHERE id = ? IF name = 'old'",
      "UPDATE t SET name = ? WHERE id = ? IF name = ?",
      "UPDATE t SET name = ? WHERE id = ? IF EXISTS"
    ),
    "DELETE and TRUNCATE statements" -> Seq(
      "DELETE FROM t WHERE id = ?",
      "DELETE FROM t WHERE id = :id",
      "DELETE name FROM t WHERE id = ?",
      "DELETE name, email FROM t WHERE id = ?",
      "DELETE date FROM t WHERE id = ?",
      "DELETE m['k'] FROM t WHERE id = ?",
      "DELETE l[0] FROM t WHERE id = ?",
      "DELETE FROM t WHERE id = ? IF EXISTS",
      "DELETE FROM t WHERE id = ? IF name = ?",
      "DELETE FROM t USING TIMESTAMP 1000 WHERE id = ?",
      "DELETE FROM t WHERE id IN (1, 2, 3)",
      "DELETE FROM t WHERE id = ? AND clustering = ?",
      "TRUNCATE t",
      "TRUNCATE TABLE t",
      "TRUNCATE TABLE ks.t"
    ),
    "BATCH statements" -> Seq(
      "BEGIN BATCH INSERT INTO t (id) VALUES (1); INSERT INTO t (id) VALUES (2); APPLY BATCH",
      "BEGIN BATCH INSERT INTO t (id) VALUES (?) INSERT INTO t (id) VALUES (?) APPLY BATCH",
      "BEGIN UNLOGGED BATCH INSERT INTO t (id) VALUES (?) APPLY BATCH",
      "BEGIN COUNTER BATCH UPDATE t SET c = c + 1 WHERE id = ? APPLY BATCH",
      "BEGIN BATCH USING TIMESTAMP 1000 INSERT INTO t (id) VALUES (1) APPLY BATCH",
      "BEGIN BATCH UPDATE t SET name = ? WHERE id = ?; DELETE FROM t WHERE id = ?; APPLY BATCH"
    ),
    // multi-statement BATCH blocks. Closed by the Cassandra 5.0 grammar migration
    // (`batchStatement` models the whole block); pinned here so a future re-import that regresses it
    // turns red. The separator `;` is optional, mixed statement types are allowed, and the block
    // takes optional UNLOGGED/COUNTER modifiers and a USING clause. A malformed batch (missing
    // APPLY BATCH) is rejected, see `CqlValidatorSpec`.
    "A3 multi-statement BATCH" -> Seq(
      "BEGIN BATCH INSERT INTO t (id) VALUES (1) APPLY BATCH",
      "BEGIN BATCH INSERT INTO t (id) VALUES (1); UPDATE t SET n = 1 WHERE id = 2; DELETE FROM t WHERE id = 3; APPLY BATCH",
      "BEGIN UNLOGGED BATCH INSERT INTO t (id) VALUES (1); INSERT INTO t (id) VALUES (2) APPLY BATCH",
      "BEGIN COUNTER BATCH UPDATE t SET c = c + 1 WHERE id = 1; UPDATE t SET c = c + 2 WHERE id = 2 APPLY BATCH",
      "BEGIN BATCH USING TIMESTAMP 1000 INSERT INTO t (id) VALUES (1); UPDATE t SET n = 2 WHERE id = 1 APPLY BATCH",
      "BEGIN UNLOGGED BATCH USING TIMESTAMP 1000 INSERT INTO t (id) VALUES (1) APPLY BATCH",
      "BEGIN BATCH INSERT INTO t (id, n) VALUES (?, ?); UPDATE t SET n = ? WHERE id = ?; APPLY BATCH",
      "BEGIN BATCH INSERT INTO t (id) VALUES (1) USING TTL 60; INSERT INTO t (id) VALUES (2) APPLY BATCH"
    ),
    "CREATE TABLE with native data types" -> Seq(
      "CREATE TABLE t (id ASCII PRIMARY KEY)",
      "CREATE TABLE t (id BIGINT PRIMARY KEY)",
      "CREATE TABLE t (id BLOB PRIMARY KEY)",
      "CREATE TABLE t (id BOOLEAN PRIMARY KEY)",
      "CREATE TABLE t (id DATE PRIMARY KEY)",
      "CREATE TABLE t (id DECIMAL PRIMARY KEY)",
      "CREATE TABLE t (id DOUBLE PRIMARY KEY)",
      "CREATE TABLE t (id DURATION PRIMARY KEY)",
      "CREATE TABLE t (id FLOAT PRIMARY KEY)",
      "CREATE TABLE t (id INET PRIMARY KEY)",
      "CREATE TABLE t (id INT PRIMARY KEY)",
      "CREATE TABLE t (id SMALLINT PRIMARY KEY)",
      "CREATE TABLE t (id TEXT PRIMARY KEY)",
      "CREATE TABLE t (id TIME PRIMARY KEY)",
      "CREATE TABLE t (id TIMESTAMP PRIMARY KEY)",
      "CREATE TABLE t (id TIMEUUID PRIMARY KEY)",
      "CREATE TABLE t (id TINYINT PRIMARY KEY)",
      "CREATE TABLE t (id UUID PRIMARY KEY)",
      "CREATE TABLE t (id VARCHAR PRIMARY KEY)",
      "CREATE TABLE t (id VARINT PRIMARY KEY)"
    ),
    "CREATE TABLE with collection and parameterized types" -> Seq(
      "CREATE TABLE t (id INT PRIMARY KEY, s SET<TEXT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, l LIST<INT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, INT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, s SET<TEXT>, l LIST<INT>, m MAP<TEXT, INT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<LIST<INT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<SET<TEXT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<MAP<TEXT, INT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<LIST<INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, LIST<INT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup TUPLE<INT, TEXT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup TUPLE<INT, TEXT, FLOAT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup FROZEN<TUPLE<INT, TEXT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, v VECTOR<FLOAT, 3>)",
      "CREATE TABLE t (id INT PRIMARY KEY, nested LIST<FROZEN<MAP<TEXT, INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, addr FROZEN<address>)"
    ),
    // nested and frozen parameterized collection types. Closed by the Cassandra 5.0
    // grammar migration (`comparatorType` is fully recursive); pinned here so a future re-import
    // that flattens it turns red. `VECTOR<FLOAT, n>` is covered in the group above.
    "A2 nested and frozen parameterized collection types" -> Seq(
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<LIST<INT>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<LIST<INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<SET<INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, s SET<FROZEN<SET<INT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, l LIST<FROZEN<TUPLE<INT, TEXT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, m MAP<TEXT, FROZEN<MAP<INT, TEXT>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup TUPLE<INT, TEXT>)",
      "CREATE TABLE t (id INT PRIMARY KEY, tup FROZEN<TUPLE<INT, FROZEN<LIST<TEXT>>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, f FROZEN<MAP<TEXT, FROZEN<SET<INT>>>>)",
      "CREATE TABLE t (id INT PRIMARY KEY, addr FROZEN<address>)",
      "CREATE TYPE ut (a FROZEN<LIST<INT>>, b FROZEN<MAP<TEXT, INT>>)",
      "ALTER TABLE t ADD data MAP<TEXT, FROZEN<LIST<INT>>>"
    ),
    "CREATE TABLE" -> Seq(
      "CREATE TABLE t (id INT PRIMARY KEY, name TEXT)",
      "CREATE TABLE IF NOT EXISTS ks.t (id UUID PRIMARY KEY)",
      "CREATE TABLE ks.t (id UUID PRIMARY KEY, name TEXT)",
      "CREATE COLUMNFAMILY t (id INT PRIMARY KEY)",
      "CREATE TABLE t (id UUID, cluster_key TEXT, val INT, PRIMARY KEY (id, cluster_key))",
      "CREATE TABLE t (a INT, b INT, c INT, PRIMARY KEY ((a, b), c))",
      "CREATE TABLE t (a INT, b INT, c INT, d INT, PRIMARY KEY ((a, b), c, d))",
      "CREATE TABLE t (id INT, c INT, s TEXT STATIC, PRIMARY KEY (id, c))",
      "CREATE TABLE t (id INT PRIMARY KEY, s TEXT STATIC)",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH comment = 'x'",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH comment = 'x' AND gc_grace_seconds = 100",
      "CREATE TABLE t (id INT, c INT, PRIMARY KEY (id, c)) WITH CLUSTERING ORDER BY (c DESC)",
      "CREATE TABLE t (id INT, c INT, PRIMARY KEY (id, c)) WITH CLUSTERING ORDER BY (c ASC) AND comment = 'x'",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH compaction = {'class': 'SizeTieredCompactionStrategy'}",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH compression = {'sstable_compression': 'LZ4Compressor'}",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH default_time_to_live = 3600",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH bloom_filter_fp_chance = 0.01"
    ),
    "ALTER TABLE and DROP TABLE" -> Seq(
      "ALTER TABLE t ADD age INT",
      "ALTER TABLE ks.t ADD age INT",
      "ALTER TABLE t ADD (age INT, city TEXT)",
      "ALTER TABLE t ADD tags SET<TEXT>",
      "ALTER TABLE t DROP age",
      "ALTER TABLE t DROP (age, city)",
      "ALTER TABLE t RENAME old TO new",
      "ALTER TABLE t WITH comment = 'updated'",
      "ALTER TABLE t WITH gc_grace_seconds = 0",
      "DROP TABLE t",
      "DROP TABLE IF EXISTS t",
      "DROP TABLE ks.t",
      "DROP COLUMNFAMILY t"
    ),
    "CREATE / ALTER / DROP KEYSPACE" -> Seq(
      "CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}",
      "CREATE KEYSPACE IF NOT EXISTS ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}",
      "CREATE KEYSPACE ks WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3}",
      "CREATE KEYSPACE ks WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3, 'dc2': 2}",
      "CREATE KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1} AND durable_writes = true",
      "CREATE KEYSPACE IF NOT EXISTS ks WITH replication = {'class': 'NetworkTopologyStrategy', 'dc1': 3}",
      "ALTER KEYSPACE ks WITH durable_writes = true",
      "ALTER KEYSPACE ks WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2}",
      "DROP KEYSPACE ks",
      "DROP KEYSPACE IF EXISTS ks",
      "USE ks",
      "USE my_keyspace"
    ),
    "User-defined types" -> Seq(
      "CREATE TYPE addr (street TEXT, zip INT)",
      "CREATE TYPE IF NOT EXISTS addr (street TEXT, zip INT)",
      "CREATE TYPE ks.addr (street TEXT, zip INT)",
      "CREATE TYPE addr (street TEXT, city TEXT, zip INT, country TEXT)",
      "ALTER TYPE addr ADD country TEXT",
      "ALTER TYPE addr RENAME zip TO postcode",
      "DROP TYPE addr",
      "DROP TYPE IF EXISTS addr"
    ),
    "Indexes" -> Seq(
      "CREATE INDEX ON t (name)",
      "CREATE INDEX idx ON ks.t (name)",
      "CREATE INDEX IF NOT EXISTS idx ON t (name)",
      "CREATE INDEX ON t (KEYS(m))",
      "CREATE INDEX ON t (VALUES(m))",
      "CREATE INDEX ON t (ENTRIES(m))",
      "CREATE INDEX ON t (FULL(l))",
      "CREATE CUSTOM INDEX ON t (name) USING 'org.apache.cassandra.index.sasi.SASIIndex'",
      "CREATE CUSTOM INDEX ON t (name) USING 'StorageAttachedIndex'",
      "DROP INDEX idx",
      "DROP INDEX IF EXISTS idx",
      "DROP INDEX ks.idx"
    ),
    "Materialized views" -> Seq(
      "CREATE MATERIALIZED VIEW mv AS SELECT id, name FROM t WHERE id IS NOT NULL AND name IS NOT NULL PRIMARY KEY (name, id)",
      "CREATE MATERIALIZED VIEW IF NOT EXISTS mv AS SELECT * FROM t WHERE id IS NOT NULL PRIMARY KEY (id)",
      "CREATE MATERIALIZED VIEW mv AS SELECT id, name FROM t WHERE id IS NOT NULL AND name IS NOT NULL PRIMARY KEY (name, id) WITH CLUSTERING ORDER BY (id ASC)",
      "ALTER MATERIALIZED VIEW mv WITH comment = 'x'",
      "DROP MATERIALIZED VIEW mv",
      "DROP MATERIALIZED VIEW IF EXISTS mv"
    ),
    "Functions and aggregates" -> Seq(
      "CREATE FUNCTION f (i INT) CALLED ON NULL INPUT RETURNS INT LANGUAGE java AS 'return i;'",
      "CREATE FUNCTION f (i INT) RETURNS NULL ON NULL INPUT RETURNS INT LANGUAGE java AS 'return i;'",
      "CREATE OR REPLACE FUNCTION f (i INT) CALLED ON NULL INPUT RETURNS INT LANGUAGE java AS 'return i;'",
      "CREATE FUNCTION IF NOT EXISTS f (i INT) CALLED ON NULL INPUT RETURNS INT LANGUAGE java AS 'return i;'",
      "CREATE FUNCTION f (a INT, b INT) CALLED ON NULL INPUT RETURNS INT LANGUAGE java AS 'return a + b;'",
      "DROP FUNCTION f",
      "DROP FUNCTION IF EXISTS f",
      "DROP FUNCTION f (INT)",
      "CREATE AGGREGATE agg (INT) SFUNC sf STYPE INT INITCOND 0",
      "CREATE OR REPLACE AGGREGATE agg (INT) SFUNC sf STYPE INT",
      "CREATE AGGREGATE IF NOT EXISTS agg (INT) SFUNC sf STYPE INT FINALFUNC ff INITCOND 0",
      "DROP AGGREGATE agg",
      "DROP AGGREGATE IF EXISTS agg",
      "DROP AGGREGATE agg (INT)"
    ),
    "Roles and users" -> Seq(
      "CREATE ROLE r",
      "CREATE ROLE IF NOT EXISTS r",
      "CREATE ROLE r WITH PASSWORD = 'x'",
      "CREATE ROLE r WITH PASSWORD = 'x' AND LOGIN = true",
      "CREATE ROLE r WITH PASSWORD = 'x' AND LOGIN = true AND SUPERUSER = false",
      "ALTER ROLE r WITH PASSWORD = 'y'",
      "DROP ROLE r",
      "DROP ROLE IF EXISTS r",
      "LIST ROLES",
      "LIST ROLES OF r",
      "CREATE USER u WITH PASSWORD 'x'",
      "CREATE USER IF NOT EXISTS u WITH PASSWORD 'x' SUPERUSER",
      "ALTER USER u WITH PASSWORD 'y'",
      "DROP USER u",
      "DROP USER IF EXISTS u",
      "LIST USERS"
    ),
    "Permissions" -> Seq(
      "GRANT SELECT ON KEYSPACE ks TO r",
      "GRANT ALL PERMISSIONS ON TABLE t TO r",
      "GRANT ALL ON TABLE t TO r",
      "GRANT MODIFY ON t TO r",
      "GRANT SELECT ON ALL KEYSPACES TO r",
      "REVOKE MODIFY ON t FROM r",
      "REVOKE SELECT ON KEYSPACE ks FROM r",
      "REVOKE ALL PERMISSIONS ON TABLE t FROM r",
      "GRANT r TO other",
      "REVOKE r FROM other",
      "LIST ALL PERMISSIONS",
      "LIST ALL PERMISSIONS OF r",
      "LIST SELECT ON TABLE t OF r"
    ),
    "Literals in terms" -> Seq(
      "INSERT INTO t (id, v) VALUES (1, 'text')",
      "INSERT INTO t (id, v) VALUES (1, 42)",
      "INSERT INTO t (id, v) VALUES (1, -42)",
      "INSERT INTO t (id, v) VALUES (1, 3.14)",
      "INSERT INTO t (id, v) VALUES (1, -3.14)",
      "INSERT INTO t (id, v) VALUES (1, true)",
      "INSERT INTO t (id, v) VALUES (1, false)",
      "INSERT INTO t (id, v) VALUES (1, null)",
      "INSERT INTO t (id, v) VALUES (1, 0x00ff)",
      "INSERT INTO t (id, v) VALUES (1, 550e8400-e29b-41d4-a716-446655440000)",
      "INSERT INTO t (id, v) VALUES (1, '2011-02-03')",
      "INSERT INTO t (id, v) VALUES (1, '2011-02-03 04:05:00')",
      "INSERT INTO t (id, v) VALUES (1, {'a', 'b', 'c'})",
      "INSERT INTO t (id, v) VALUES (1, {'k1': 1, 'k2': 2})",
      "INSERT INTO t (id, v) VALUES (1, [1, 2, 3])",
      "INSERT INTO t (id, v) VALUES (1, [])",
      "INSERT INTO t (id, v) VALUES (1, (1, 'a', true))",
      "INSERT INTO t (id, v) VALUES (1, {street: '1 st', zip: 1000})",
      "SELECT id FROM t WHERE s = 'it''s escaped'"
    ),
    "Multi-line statements" -> Seq(
      "SELECT id, name\nFROM t\nWHERE id = ?",
      "INSERT INTO t (id, name)\nVALUES (?, ?)",
      "UPDATE t\nSET name = ?\nWHERE id = ?",
      "CREATE TABLE t (\n  id INT PRIMARY KEY,\n  name TEXT\n)"
    ),
    // constructs surfaced by triaging a broad candidate set against the C2 oracle and
    // confirmed valid on the embedded (3.11-compatible) Cassandra. These are the "gaps we did not
    // find by hand", trailing separators, comments, quoted identifiers, arithmetic assignments,
    // duration/scientific literals, bind markers in USING/tuple/condition positions, and DDL option
    // and auth-scope variants.
    "A4 additional constructs (oracle-confirmed)" -> Seq(
      "SELECT * FROM t;",
      "SELECT id FROM t /* filter */ WHERE id = ?",
      "SELECT /* projection */ id FROM t",
      "SELECT \"select\" FROM t",
      "SELECT \"Column\" FROM \"MyTable\"",
      "SELECT * FROM \"ks\".\"t\" WHERE \"id\" = ?",
      "SELECT (int)1 FROM t",
      "SELECT DISTINCT token(id) FROM t",
      "SELECT CAST(writetime(name) AS bigint) FROM t",
      "INSERT INTO t (id, v) VALUES (1, 1.5e10)",
      "INSERT INTO t (id, v) VALUES (1, 1.5E-10)",
      "INSERT INTO t (id, d) VALUES (1, 89h4m48s)",
      "INSERT INTO t (id, d) VALUES (1, P1Y2M3D)",
      "INSERT INTO t (id, d) VALUES (1, 12h30m)",
      "INSERT INTO t (id, v) VALUES (1, (bigint) 2)",
      "INSERT INTO t (id, ts) VALUES (?, currentTimestamp())",
      "INSERT INTO t (id, d) VALUES (?, currentDate())",
      "INSERT INTO t (id) VALUES (?) USING TTL ? AND TIMESTAMP ?",
      "UPDATE t USING TTL ? SET n = ? WHERE id = ?",
      "DELETE FROM t USING TIMESTAMP ? WHERE id = ?",
      "SELECT * FROM t WHERE (a, b) IN ?",
      "SELECT * FROM t WHERE (a, b) >= (?, ?)",
      "SELECT * FROM t WHERE a = ? AND (b, c) > (?, ?)",
      "UPDATE t SET n = ? WHERE id = ? IF n > 0",
      "UPDATE t SET n = ? WHERE id = ? IF n IN (1, 2, 3)",
      "UPDATE t SET n = ? WHERE id = ? IF m['k'] = ?",
      "DELETE FROM t WHERE id = ? IF n != 1",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH ID = '5a1c395e-b41f-11e5-9f22-ba0be0483c18'",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}",
      "CREATE INDEX ON t (name) USING 'sai'",
      "CREATE CUSTOM INDEX ON t (v) USING 'StorageAttachedIndex' WITH OPTIONS = {'similarity_function': 'cosine'}",
      "ALTER TABLE t RENAME a TO b AND c TO d",
      "ALTER TABLE t ALTER n TYPE BLOB",
      "CREATE ROLE r WITH PASSWORD = 'x' AND OPTIONS = {'opt': 'val'}",
      "ALTER ROLE r WITH SUPERUSER = true",
      "GRANT DESCRIBE ON ALL ROLES TO r",
      "GRANT EXECUTE ON ALL FUNCTIONS TO r",
      "REVOKE AUTHORIZE ON KEYSPACE ks FROM r"
    ),
    // genuinely-valid Cassandra 5.0 constructs the grammar targets but the older
    // embedded oracle (3.11) rejects. They appear as the '''benign''' direction in
    // `CqlDifferentialSpec` (validator accepts, embedded Cassandra rejects), that is expected and
    // logged, not a failure.
    "Cassandra 5.0-only constructs (benign against older oracles)" -> Seq(
      "SELECT n + 1 FROM t",
      "SELECT id FROM t WHERE n = 1 + 2",
      "SELECT * FROM t ORDER BY v ANN OF [0.1, 0.2, 0.3] LIMIT 5",
      "CREATE TABLE t (id INT PRIMARY KEY) WITH read_repair = 'BLOCKING'",
      "CREATE TABLE t (id INT PRIMARY KEY, n TEXT MASKED WITH mask_default())",
      "ALTER TABLE t ALTER n MASKED WITH mask_default()",
      "CREATE ROLE r WITH LOGIN = true AND ACCESS TO DATACENTERS {'dc1'}",
      "GRANT SELECT ON ALL TABLES IN KEYSPACE ks TO r",
      "DESCRIBE KEYSPACES",
      "DESCRIBE KEYSPACE ks",
      "DESCRIBE TABLE t",
      "DESCRIBE CLUSTER"
    )
  )

  /** Every corpus query, flattened across groups. */
  val queries: Seq[String] = groups.flatMap(_._2)
}
