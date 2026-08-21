/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import org.scalacheck.Gen

/** ScalaCheck generators of CQL for the fuzzing / property tests.
  *
  * The generators are '''template-driven''': they assemble statements from grammar-shaped pieces
  * (identifiers, data types, literals, terms, relations, selectors) with random structure, nesting
  * and arity. They aim to emit CQL that is '''valid''' — accepted by both [[CqlValidator]] and a real
  * Cassandra — so the differential property has something to disagree about; invalid emissions are
  * harmless there (both sides reject).
  *
  * Two deliberate exclusions keep the generators honest against the embedded 3.11 oracle and the one
  * documented deviation:
  *
  *   - no '''bind markers in `SELECT` selector position''' (bare `?`, or as a function argument),
  *     which helenus intentionally rejects (see `tools/antlr-import/README.md`);
  *   - no constructs known to be '''5.0-only''' (arithmetic, element selection, `VECTOR`, ...), which
  *     the 3.11 oracle rejects — those are covered, and logged as benign, by the corpus in
  *     `CqlDifferentialSpec`.
  */
object CqlGenerators {

  /** Non-reserved identifiers safe to use unquoted, plus the occasional quoted one. */
  private val bareIdent: Gen[String] =
    Gen.oneOf(
      "id",
      "name",
      "email",
      "a",
      "b",
      "c",
      "x",
      "y",
      "val",
      "data",
      "ts",
      "n",
      "m",
      "l",
      "s",
      "tags",
      "addr",
      "score",
      "created",
      "updated",
      "amount",
      "status",
      "code"
    )

  val ident: Gen[String] =
    Gen.frequency(
      9 -> bareIdent,
      1 -> bareIdent.map(i => "\"" + i.capitalize + "\"") // quoted, case-preserving
    )

  val tableName: Gen[String] =
    Gen.frequency(
      3 -> ident,
      1 -> (for { ks <- bareIdent; t <- bareIdent } yield s"$ks.$t")
    )

  private val nativeType: Gen[String] =
    Gen.oneOf(
      "INT",
      "BIGINT",
      "TEXT",
      "VARCHAR",
      "UUID",
      "TIMEUUID",
      "BOOLEAN",
      "DOUBLE",
      "FLOAT",
      "TIMESTAMP",
      "DATE",
      "TIME",
      "BLOB",
      "DECIMAL",
      "VARINT",
      "SMALLINT",
      "TINYINT",
      "INET",
      "ASCII"
    )

  /** Recursive data type, depth-bounded — the targeted generator for nested / frozen types. */
  def dataType(depth: Int): Gen[String] =
    if (depth <= 0) nativeType
    else
      Gen.frequency(
        6 -> nativeType,
        1 -> dataType(depth - 1).map(t => s"LIST<$t>"),
        1 -> dataType(depth - 1).map(t => s"SET<$t>"),
        1 -> (for { k <- dataType(depth - 1); v <- dataType(depth - 1) } yield s"MAP<$k, $v>"),
        1 -> (for {
          n <- Gen.choose(1, 3)
          ts <- Gen.listOfN(n, dataType(depth - 1))
        } yield ts.mkString("TUPLE<", ", ", ">")),
        1 -> dataType(depth - 1).map(t => s"FROZEN<$t>")
      )

  private val safeString: Gen[String] =
    Gen.listOf(Gen.alphaNumChar).map(cs => "'" + cs.take(12).mkString + "'")

  private val scalarLiteral: Gen[String] =
    Gen.oneOf(
      Gen.choose(-1000000L, 1000000L).map(_.toString),
      Gen.choose(-100000L, 100000L).map(v => s"$v.${math.abs(v % 1000)}"),
      safeString,
      Gen.oneOf("true", "false"),
      Gen.const("null"),
      Gen.uuid.map(_.toString),
      Gen.const("0x00ff")
    )

  /** Recursive literal, depth-bounded — the targeted generator for (nested) collection literals. */
  def literal(depth: Int): Gen[String] =
    if (depth <= 0) scalarLiteral
    else {
      def elems: Gen[List[String]] = Gen.choose(0, 3).flatMap(Gen.listOfN(_, literal(depth - 1)))
      Gen.frequency(
        6 -> scalarLiteral,
        1 -> elems.map(_.mkString("{", ", ", "}")), // set literal
        1 -> elems.map(_.mkString("[", ", ", "]")), // list literal
        1 -> Gen // map literal
          .choose(1, 3)
          .flatMap(
            Gen.listOfN(_, for { k <- literal(depth - 1); v <- literal(depth - 1) } yield s"$k: $v")
          )
          .map(_.mkString("{", ", ", "}")),
        1 -> Gen // tuple literal
          .choose(1, 3)
          .flatMap(Gen.listOfN(_, literal(depth - 1)))
          .map(_.mkString("(", ", ", ")"))
      )
    }

  /** A term in a value position (WHERE right-hand side, VALUES, SET): literals, bind markers and
    * function calls are all valid here (unlike selector position).
    */
  def term(depth: Int): Gen[String] =
    Gen.frequency(
      5 -> literal(depth),
      2 -> Gen.const("?"),
      2 -> ident.map(":" + _.replace("\"", "")),
      1 -> functionTerm
    )

  private val functionTerm: Gen[String] =
    Gen.oneOf(
      Gen.const("now()"),
      Gen.const("uuid()"),
      Gen.const("toTimestamp(now())"),
      Gen.choose(1, 2).flatMap(Gen.listOfN(_, bareIdent)).map(_.mkString("token(", ", ", ")"))
    )

  /** A term with no bind marker (used where a marker would be invalid, e.g. selector-position
    * function arguments).
    */
  private def markerlessTerm(depth: Int): Gen[String] =
    Gen.frequency(4 -> literal(depth), 1 -> bareIdent)

  /** A selector: never a bare bind marker nor a marker function argument (the intentional
    * deviation). Element access and arithmetic are omitted (5.0-only against the 3.11 oracle).
    */
  private val selectorItem: Gen[String] =
    Gen.frequency(
      5 -> ident,
      2 -> (for { c <- ident; a <- bareIdent } yield s"$c AS $a"),
      1 -> Gen.const("count(*)"),
      1 -> (for {
        f <- Gen.oneOf("token", "count", "avg", "min", "max", "sum")
        args <- Gen.choose(1, 2).flatMap(Gen.listOfN(_, markerlessTerm(1)))
      } yield args.mkString(s"$f(", ", ", ")"))
    )

  private val selectors: Gen[String] =
    Gen.frequency(
      2 -> Gen.const("*"),
      3 -> Gen.choose(1, 4).flatMap(Gen.listOfN(_, selectorItem)).map(_.mkString(", "))
    )

  private val relationOp: Gen[String] = Gen.oneOf("=", "<", ">", "<=", ">=")

  private val relation: Gen[String] =
    Gen.frequency(
      5 -> (for { c <- ident; op <- relationOp; t <- term(1) } yield s"$c $op $t"),
      1 -> (for {
        c <- ident
        n <- Gen.choose(1, 3)
        ts <- Gen.listOfN(n, term(0))
      } yield ts.mkString(s"$c IN (", ", ", ")")),
      1 -> ident.map(c => s"$c IN ?"),
      1 -> (for {
        cols <- Gen.choose(1, 2).flatMap(Gen.listOfN(_, bareIdent))
        args <- Gen.listOfN(cols.size, term(0))
        op <- relationOp
      } yield s"token(${cols.mkString(", ")}) $op token(${args.mkString(", ")})")
    )

  private val whereClause: Gen[String] =
    Gen.choose(1, 3).flatMap(Gen.listOfN(_, relation)).map(rs => "WHERE " + rs.mkString(" AND "))

  private val usingClause: Gen[String] =
    Gen.oneOf(
      Gen.choose(1, 100000).map(ttl => s"USING TTL $ttl"),
      Gen.const("USING TTL ?"),
      Gen.choose(1L, 1000000L).map(ts => s"USING TIMESTAMP $ts"),
      for {
        ttl <- Gen.choose(1, 100000); ts <- Gen.choose(1L, 1000000L)
      } yield s"USING TTL $ttl AND TIMESTAMP $ts"
    )

  val select: Gen[String] =
    for {
      distinct <- Gen.oneOf("", "DISTINCT ")
      sel <- selectors
      table <- tableName
      where <- Gen.oneOf(Gen.const(""), whereClause.map(" " + _))
      order <- Gen.oneOf(
        Gen.const(""),
        for { c <- ident; d <- Gen.oneOf("ASC", "DESC") } yield s" ORDER BY $c $d"
      )
      limit <- Gen.oneOf(
        Gen.const(""),
        Gen.choose(1, 1000).map(n => s" LIMIT $n"),
        Gen.const(" LIMIT ?")
      )
      filter <- Gen.oneOf("", " ALLOW FILTERING")
    } yield s"SELECT $distinct$sel FROM $table$where$order$limit$filter"

  val insert: Gen[String] =
    for {
      table <- tableName
      n <- Gen.choose(1, 4)
      cols <- Gen.listOfN(n, ident)
      vals <- Gen.listOfN(n, term(2))
      ifne <- Gen.oneOf("", " IF NOT EXISTS")
      using <- Gen.oneOf(Gen.const(""), usingClause.map(" " + _))
    } yield s"INSERT INTO $table (${cols.mkString(", ")}) VALUES (${vals.mkString(", ")})$ifne$using"

  val update: Gen[String] =
    for {
      table <- tableName
      using <- Gen.oneOf(Gen.const(""), usingClause.map(" " + _))
      n <- Gen.choose(1, 3)
      sets <- Gen.listOfN(n, for { c <- ident; v <- term(2) } yield s"$c = $v")
      where <- whereClause
      cond <- Gen.oneOf(
        Gen.const(""),
        Gen.const(" IF EXISTS"),
        for { c <- ident; v <- term(0) } yield s" IF $c = $v"
      )
    } yield s"UPDATE $table$using SET ${sets.mkString(", ")} $where$cond"

  val delete: Gen[String] =
    for {
      cols <- Gen.oneOf(
        Gen.const(""),
        Gen.choose(1, 3).flatMap(Gen.listOfN(_, ident)).map(_.mkString(" ", ", ", ""))
      )
      table <- tableName
      using <- Gen.oneOf(Gen.const(""), usingClause.map(" " + _))
      where <- whereClause
      cond <- Gen.oneOf("", " IF EXISTS")
    } yield s"DELETE$cols FROM $table$using $where$cond"

  val createTable: Gen[String] =
    for {
      ine <- Gen.oneOf("", "IF NOT EXISTS ")
      table <- tableName
      n <- Gen.choose(1, 4)
      cols <- Gen.listOfN(n, for { c <- ident; t <- dataType(2) } yield s"$c $t")
      pk <- ident
    } yield s"CREATE TABLE $ine$table (pk_col UUID PRIMARY KEY, ${cols.mkString(", ")})"

  val batch: Gen[String] =
    for {
      kind <- Gen.oneOf("", "UNLOGGED ", "COUNTER ")
      using <- Gen.oneOf(Gen.const(""), usingClause.map(_ + " "))
      n <- Gen.choose(1, 3)
      stmts <- Gen.listOfN(n, Gen.oneOf(insert, update, delete))
    } yield s"BEGIN ${kind}BATCH $using${stmts.mkString("; ")} APPLY BATCH"

  /** Any valid-shaped statement. */
  val statement: Gen[String] =
    Gen.frequency(
      5 -> select,
      4 -> insert,
      4 -> update,
      3 -> delete,
      3 -> createTable,
      2 -> batch
    )
}
