/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.bench.cql

import java.util.concurrent.TimeUnit

import net.nmoncho.helenus.internal.cql.CqlValidator
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
class CqlValidatorBenchmark {

  // --- SELECT ---

  @Benchmark
  def selectSimple(): Either[(String, Int), Unit] =
    CqlValidator.validate("SELECT id, name FROM users WHERE id = 1")

  @Benchmark
  def selectStar(): Either[(String, Int), Unit] =
    CqlValidator.validate("SELECT * FROM users")

  @Benchmark
  def selectPositionalBind(): Either[(String, Int), Unit] =
    CqlValidator.validate("SELECT * FROM users WHERE id = ?")

  @Benchmark
  def selectNamedBind(): Either[(String, Int), Unit] =
    CqlValidator.validate("SELECT * FROM users WHERE id = :id")

  @Benchmark
  def selectComplex(): Either[(String, Int), Unit] =
    CqlValidator.validate(
      "SELECT id, name, age FROM users WHERE partition_key = ? AND cluster_key > ?" +
        " ORDER BY cluster_key ASC LIMIT 100 ALLOW FILTERING"
    )

  // --- INSERT ---

  @Benchmark
  def insertLiteral(): Either[(String, Int), Unit] =
    CqlValidator.validate("INSERT INTO users (id, name) VALUES (1, 'Alice')")

  @Benchmark
  def insertPositionalBinds(): Either[(String, Int), Unit] =
    CqlValidator.validate("INSERT INTO users (id, name, age) VALUES (?, ?, ?)")

  @Benchmark
  def insertNamedBinds(): Either[(String, Int), Unit] =
    CqlValidator.validate("INSERT INTO users (id, name, age) VALUES (:id, :name, :age)")

  // --- UPDATE ---

  @Benchmark
  def updateLiteral(): Either[(String, Int), Unit] =
    CqlValidator.validate("UPDATE users SET name = 'Bob' WHERE id = 1")

  @Benchmark
  def updatePositionalBinds(): Either[(String, Int), Unit] =
    CqlValidator.validate("UPDATE users SET name = ?, age = ? WHERE id = ?")

  // --- DELETE ---

  @Benchmark
  def deleteLiteral(): Either[(String, Int), Unit] =
    CqlValidator.validate("DELETE FROM users WHERE id = 1")

  @Benchmark
  def deletePositionalBind(): Either[(String, Int), Unit] =
    CqlValidator.validate("DELETE FROM users WHERE id = ?")

  // --- DDL ---

  @Benchmark
  def createTable(): Either[(String, Int), Unit] =
    CqlValidator.validate(
      "CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY, name TEXT, age INT)"
    )

  @Benchmark
  def createKeyspace(): Either[(String, Int), Unit] =
    CqlValidator.validate(
      "CREATE KEYSPACE IF NOT EXISTS mykeyspace WITH REPLICATION = {'class': 'SimpleStrategy', 'replication_factor': 1}"
    )
}
