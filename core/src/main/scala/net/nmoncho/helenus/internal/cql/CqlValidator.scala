/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import scala.annotation.tailrec

import org.antlr.v4.runtime._

/** CQL Validator without requiring connection to Cassandra.
  *
  * This is a best-effort, offline syntactic check. It is the correctness boundary for the
  * compile-time `cql"..."` interpolator and `"...".toCQL`, so its bias matters: '''accepting'''
  * invalid CQL is harmless (the server rejects it at runtime), but '''rejecting''' valid CQL turns
  * into a compile error the user cannot bypass on that path.
  *
  * ==Grammar provenance==
  *
  * The lexer and parser are '''derived from Apache Cassandra's own ANTLR grammars''' (see
  * `tools/antlr-import/`), mechanically converted to recognizer-only ANTLR4: every embedded Java
  * action, `returns` clause and AST-building hook is stripped, keeping just the syntax. The pinned
  * Cassandra version lives in `tools/antlr-import/upstream/VERSION`, and re-importing a newer
  * grammar is a matter of re-running `tools/antlr-import/convert.py`. Because this is Cassandra's
  * real grammar, coverage tracks the target CQL dialect directly rather than a hand-maintained
  * approximation.
  *
  * ==Scope==
  *
  * The check is '''syntactic only'''. It runs with no schema, keyspace or types, so it cannot verify
  * that a column exists or that a value's type matches — the server does that at runtime. When a
  * construct is rejected but you believe it is valid (e.g. a newer-dialect or DSE-only feature not in
  * the pinned grammar), bypass validation with `"...".toUnsafeCQL` (or `toUnsafeCQLAsync`), which
  * builds the statement without this check.
  */
object CqlValidator {

  /** Validates an input CQL string
    *
    * @param input input to validate
    * @return if CQL is invalid, an error message and a position where the error happened. Unit otherwise.
    */
  def validate(input: String): Either[(String, Int), Unit] =
    firstError(input)
      .map { case (msg, charPositionInLine, _) => msg -> charPositionInLine }
      .toLeft(())

  /** Position of the first syntax error within the entire input, if any.
    *
    * [[validate]] reports the position within the offending line, which is all a compiler error
    * needs. Callers that assemble a statement out of several pieces need to know *which* piece
    * the parser is complaining about, and only a position within the whole input can tell them
    * that on a multi-line statement.
    *
    * @param input input to validate
    * @return position of the offending token, or [[None]] if the input is valid
    */
  private[helenus] def firstErrorOffset(input: String): Option[Int] =
    firstError(input).map { case (_, _, offset) => offset }

  /** Positions at which the input has a named bind marker (`:name`).
    *
    * A statement can parse with a `:name` in it that isn't a bind marker at all, because it sits
    * inside a string literal or a comment, so callers that write markers into a statement need a
    * way to tell whether the one they wrote survived as a token.
    *
    * @param input input to tokenize
    * @return position at which each named bind marker starts
    */
  private[helenus] def bindMarkerOffsets(input: String): Set[Int] = {
    val lexer = new CqlLexer(CharStreams.fromString(input))
    lexer.removeErrorListeners()

    val tokens = new CommonTokenStream(lexer)
    scala.util.Try(tokens.fill())

    @tailrec
    def inner(idx: Int, acc: Set[Int]): Set[Int] =
      if (idx >= tokens.size()) acc
      else {
        val token = tokens.get(idx)
        // A named bind marker `:name` lexes as COLON immediately followed by an
        // identifier (Cassandra's grammar has no single named-marker token).
        // Require the name to sit right against the colon so a stray `:` (or a
        // `: name` with a space) isn't mistaken for a marker.
        val isNamedMarker =
          token.getType == CqlLexer.COLON && idx + 1 < tokens.size() && {
            val next = tokens.get(idx + 1)
            (next.getType == CqlLexer.IDENT || next.getType == CqlLexer.QUOTED_NAME) &&
            next.getStartIndex == token.getStopIndex + 1
          }

        val acc2 = if (isNamedMarker) acc + token.getStartIndex else acc
        inner(idx + 1, acc2)
      }

    inner(0, Set.empty)
  }

  /** Runs the parser, reporting the message, in-line position and absolute position of the first
    * syntax error
    */
  private def firstError(input: String): Option[(String, Int, Int)] = {
    var found: Option[(String, Int, Int)] = None

    val errorListener = new BaseErrorListener {
      override def syntaxError(
          recognizer: Recognizer[_, _],
          offendingSymbol: AnyRef,
          line: Int,
          charPositionInLine: Int,
          msg: String,
          e: RecognitionException
      ): Unit =
        if (found.isEmpty) {
          val offset = offendingSymbol match {
            case token: Token => token.getStartIndex
            // Lexer errors don't carry a token, so the offset has to be reconstructed
            case _ => lineStart(input, line) + charPositionInLine
          }

          found = Some((msg, charPositionInLine, offset))
        }
    }

    val charStream = CharStreams.fromString(input)

    val lexer = new CqlLexer(charStream)
    lexer.removeErrorListeners()
    lexer.addErrorListener(errorListener)

    val tokens = new CommonTokenStream(lexer)

    val parser = new CqlParser(tokens)
    parser.removeErrorListeners()
    parser.addErrorListener(errorListener)
    parser.setErrorHandler(new CqlErrorHandler)

    scala.util.Try(parser.root())

    found
  }

  /** Position at which the (1-based) `line` starts within `input` */
  private def lineStart(input: String, line: Int): Int = {
    @tailrec
    def inner(idx: Int, remaining: Int): Int =
      if (remaining <= 0) idx
      else
        input.indexOf('\n', idx) match {
          case -1 => idx
          case newLine => inner(newLine + 1, remaining - 1)
        }

    inner(0, line - 1)
  }

  /** Custom Error Handler to get better diagnostics
    */
  private class CqlErrorHandler extends DefaultErrorStrategy {

    override def reportNoViableAlternative(
        recognizer: Parser,
        error: NoViableAltException
    ): Unit =
      // Prefer the token where the failed decision *started* (e.g. `foo` in
      // `id = foo`). The offending token is often EOF by the time the parser
      // gives up, since it speculatively consumes input first, which would
      // misreport the error position.
      report(recognizer, Option(error.getStartToken).getOrElse(error.getOffendingToken), error)

    override def reportInputMismatch(
        recognizer: Parser,
        error: InputMismatchException
    ): Unit =
      report(recognizer, error.getOffendingToken, error)

    private def report(recognizer: Parser, token: Token, error: RecognitionException): Unit = {
      val hint = describe(recognizer.getTokenStream, token)
        .orElse(expectedHint(recognizer, error))

      val msg = hint
        .fold(s"unexpected ${display(token)}")(exp =>
          s"unexpected ${display(token)}, expected $exp"
        )

      recognizer.notifyErrorListeners(token, msg, error)
    }

    // Fallback when the previous-token heuristic has nothing to say: derive the
    // "expected" set from ANTLR itself (the parser state), mapping token types
    // to friendly names via the vocabulary. Cassandra names every keyword token
    // `K_XXX`, so stripping the prefix yields the CQL keyword (`K_SELECT` →
    // `SELECT`). Skipped when the set is too large to be useful as a suggestion.
    private def expectedHint(recognizer: Parser, error: RecognitionException): Option[String] =
      Option(error.getExpectedTokens).flatMap { expected =>
        val vocab = recognizer.getVocabulary
        val names = expected.toArray.toList.flatMap { tokenType =>
          Option(vocab.getSymbolicName(tokenType)).map(name =>
            if (name.startsWith("K_")) name.substring(2) else name
          )
        }

        if (names.nonEmpty && names.lengthCompare(20) <= 0) Some("one of: " + names.mkString(", "))
        else None
      }

    // Infer what was expected from the preceding visible token.
    private def describe(tokens: TokenStream, offending: Token): Option[String] = {
      val prev     = prevVisible(tokens, offending.getTokenIndex)
      val prevPrev = prev.flatMap(token => prevVisible(tokens, token.getTokenIndex))

      prev.map(_.getType).flatMap {
        case CqlLexer.EQ =>
          Some("a literal value, NULL, '?' or ':name'")

        case CqlLexer.LT | CqlLexer.GT | CqlLexer.LTE | CqlLexer.GTE =>
          Some("a literal value, '?' or ':name'")

        case CqlLexer.K_FROM | CqlLexer.K_INTO | CqlLexer.K_UPDATE | CqlLexer.K_DELETE =>
          Some("a table name")

        case CqlLexer.K_WHERE =>
          Some("a relation (column operator value)")

        case CqlLexer.K_SET =>
          Some("an assignment (column = value)")

        case CqlLexer.K_SELECT =>
          Some("a column list or '*'")

        case CqlLexer.COMMA =>
          // Could be a values list or a column/type list.
          // If prevPrev is a value token → values list → expected another value.
          // If prevPrev is a name token (column list, type list) → expected another name.
          prevPrev.map(_.getType) match {
            case Some(
                  CqlLexer.INTEGER | CqlLexer.FLOAT | CqlLexer.STRING_LITERAL | CqlLexer.UUID |
                  CqlLexer.K_NULL | CqlLexer.BOOLEAN | CqlLexer.QMARK
                ) =>
              Some("a literal value, NULL, '?' or ':name'")
            case _ =>
              Some("a column name or literal value")
          }

        case CqlLexer.IDENT =>
          // Most common case: a column name was just parsed and a data type is expected next
          // (e.g. CREATE TABLE (col_name HERE)).
          // Distinguish from the function-call dead-end (COMMA before the bare name).
          prevPrev.map(_.getType) match {
            case Some(CqlLexer.COMMA) =>
              Some("'(' for function call arguments, or a quoted string / bind marker as a value")
            case _ =>
              Some("a data type (e.g. TEXT, INT, UUID, LIST<...>)")
          }

        case _ =>
          None
      }
    }

    private def display(token: Token): String =
      if (token.getType == Token.EOF) "end of input" else s"'${token.getText}'"

    private def prevVisible(tokens: TokenStream, fromIndex: Int): Option[Token] = {
      @tailrec
      def inner(idx: Int): Option[Token] =
        if (idx < 0) None
        else {
          val t = tokens.get(idx)

          if (t.getChannel == Token.DEFAULT_CHANNEL) Some(t)
          else inner(idx - 1)
        }

      inner(fromIndex - 1)
    }
  }
}
