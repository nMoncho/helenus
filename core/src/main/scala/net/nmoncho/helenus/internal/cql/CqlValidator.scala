/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.cql

import scala.annotation.tailrec

import net.nmocho.helenus.internal.cql.CqlLexer
import net.nmocho.helenus.internal.cql.CqlParser
import org.antlr.v4.runtime._

/** CQL Validator without requiring connection to Cassandra
  */
object CqlValidator {

  /** Validates an input CQL string
    *
    * @param input input to validate
    * @return if CQL is invalid, an error message and a position where the error happened. Unit otherwise.
    */
  def validate(input: String): Either[(String, Int), Unit] = {
    var firstError: Option[(String, Int)] = None

    val errorListener = new BaseErrorListener {
      override def syntaxError(
          recognizer: Recognizer[_, _],
          offendingSymbol: AnyRef,
          line: Int,
          charPositionInLine: Int,
          msg: String,
          e: RecognitionException
      ): Unit =
        if (firstError.isEmpty) {
          firstError = Some((msg, charPositionInLine))
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

    firstError.toLeft(())
  }

  /** Custom Error Handler to get better diagnostics
    */
  private class CqlErrorHandler extends DefaultErrorStrategy {

    override def reportNoViableAlternative(
        recognizer: Parser,
        error: NoViableAltException
    ): Unit = {
      val token = error.getOffendingToken

      val msg = describe(recognizer.getTokenStream, token)
        .fold(s"unexpected ${display(token)}")(exp =>
          s"unexpected ${display(token)}, expected $exp"
        )

      recognizer.notifyErrorListeners(token, msg, error)
    }

    // Infer what was expected from the preceding visible token.
    // FIXME this is a very toy example, and doesn't catch complex queries. Improve!
    private def describe(tokens: TokenStream, offending: Token): Option[String] = {
      val prev     = prevVisible(tokens, offending.getTokenIndex)
      val prevPrev = prev.flatMap(token => prevVisible(tokens, token.getTokenIndex))

      prev.map(_.getType).flatMap {
        case CqlLexer.OPERATOR_EQ =>
          Some("a literal value, NULL, '?' or ':name'")

        case CqlLexer.OPERATOR_LT | CqlLexer.OPERATOR_GT | CqlLexer.OPERATOR_LTE |
            CqlLexer.OPERATOR_GTE =>
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
                  CqlLexer.DECIMAL_LITERAL | CqlLexer.FLOAT_LITERAL | CqlLexer.STRING_LITERAL |
                  CqlLexer.UUID | CqlLexer.K_NULL | CqlLexer.K_TRUE | CqlLexer.K_FALSE |
                  CqlLexer.BIND_MARKER | CqlLexer.NAMED_BIND_MARKER
                ) =>
              Some("a literal value, NULL, '?' or ':name'")
            case _ =>
              Some("a column name or literal value")
          }

        case CqlLexer.OBJECT_NAME =>
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
