/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.annotation.tailrec
import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.util.Strings
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.api.cql.WrappedBoundStatement
import net.nmoncho.helenus.internal.cql.CqlValidator

object CqlQueryInterpolation {

  def toCQL(c: blackbox.Context)(session: c.Expr[CqlSession]): c.Expr[CQLQuery] = {
    import c.universe._

    // c.prefix is Apply(CqlStringOps, List(innerExpr)); innerExpr may be a literal or
    // a chain of constant-folding calls (.stripMargin, .trim, …) wrapping a literal.
    c.prefix.tree match {
      case Apply(_, List(inner)) =>
        evalString(c)(inner) match {
          case Left((reason, pos)) =>
            c.abort(pos, s"toCQL requires a compile-time constant string: $reason")

          case Right(cql) =>
            CqlValidator.validate(cql) match {
              case Right(_) =>
                c.Expr[CQLQuery](
                  q"new _root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery($cql, $session)"
                )

              case Left((error, pos)) =>
                val site = literalPos(c)(inner)
                  .map(p => p.withPoint(p.point + pos))
                  .getOrElse(c.enclosingPosition)

                c.abort(site, s"Invalid CQL: $error")
            }
        }
      case _ =>
        c.abort(c.enclosingPosition, "toCQL requires a string literal")
    }
  }

  def toCQLAsync(c: blackbox.Context)(
      futSession: c.Expr[Future[CqlSession]],
      @unused ec: c.Expr[ExecutionContext]
  ): c.Expr[Future[CQLQuery]] = {
    import c.universe._

    // c.prefix is Apply(CqlStringOps, List(innerExpr)); innerExpr may be a literal or
    // a chain of constant-folding calls (.stripMargin, .trim, …) wrapping a literal.
    c.prefix.tree match {
      case Apply(_, List(inner)) =>
        evalString(c)(inner) match {
          case Left((reason, pos)) =>
            c.abort(pos, s"toCQLAsync requires a compile-time constant string: $reason")

          case Right(cql) =>
            CqlValidator.validate(cql) match {
              case Right(_) =>
                c.Expr[Future[CQLQuery]](
                  q"$futSession.map(session => new _root_.net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery($cql, session))"
                )

              case Left((error, pos)) =>
                val site = literalPos(c)(inner)
                  .map(p => p.withPoint(p.point + pos))
                  .getOrElse(c.enclosingPosition)

                c.abort(site, s"Invalid CQL: $error")
            }
        }
      case _ =>
        c.abort(c.enclosingPosition, "toCQLAsync requires a string literal")
    }
  }

  def cql(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(session: c.Expr[CqlSession]): c.Expr[WrappedBoundStatement[Row]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    CqlValidator.validate(stmt) match {
      case Right(_) => // do nothing, all good
      case Left((error, pos)) =>
        c.abort(
          c.enclosingPosition.withPoint(c.enclosingPosition.point + pos),
          s"Invalid CQL [$stmt]. Cause: $error"
        )
    }

    val bstmt = c.Expr[BoundStatement](
      q"$session.prepare($stmt).bind()"
    )

    val expr = bindParameters.foldLeft(bstmt) { case (stmt, (name, parameter)) =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[BoundStatement](
          q"$stmt.set($name, $parameter, $codec)"
        )
      }
    }

    c.Expr[WrappedBoundStatement[Row]](
      q"_root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement.apply($expr)"
    )
  }

  def cqlAsync(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[Future[CqlSession]],
      ec: c.Expr[ExecutionContext]
  ): c.Expr[Future[WrappedBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    CqlValidator.validate(stmt) match {
      case Right(_) => // do nothing, all good
      case Left((error, pos)) =>
        c.abort(
          c.enclosingPosition.withPoint(c.enclosingPosition.point + pos),
          s"Invalid CQL [$stmt]. Cause: $error"
        )
    }

    val pstmt = c.Expr[Future[PreparedStatement]](
      q"$session.flatMap(s => _root_.net.nmoncho.helenus.internal.compat.FutureConverters.asScala(s.prepareAsync($stmt)))"
    )

    val bounders = bindParameters.map { case (name, parameter) =>
      setBindParameter(c)(parameter) { codec =>
        c.Expr[Unit](
          q"bstmt = bstmt.set($name, $parameter, $codec)"
        )
      }
    }

    val expr = c.Expr[Future[WrappedBoundStatement[Row]]](
      q"$pstmt.map { stmt => var bstmt = stmt.bind(); ..$bounders; _root_.net.nmoncho.helenus.api.cql.WrappedBoundStatement.apply(bstmt)}($ec)"
    )

    expr
  }

  /** Builds a CQL Statement, considering:
    *   - Parameters sitting where CQL takes a value become 'Named Bound Parameters'
    *   - Parameters sitting anywhere else are injected into the query text as is, which
    *     requires them to be compile-time constants
    *
    * @param c Context
    * @param params String Interpolated parameters
    * @return CQL statement, with the name and expression of each Bound Parameter
    */
  private def buildStatement(
      c: blackbox.Context
  )(params: Seq[c.Expr[Any]]): (String, Seq[(String, c.Expr[Any])]) = {
    import c.universe._

    // All parts a String constants
    val parts = c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) =>
        rawParts.map {
          case Literal(Constant(value: String)) => value
          case other =>
            c.abort(
              other.pos,
              s"Expected a constant string part in the CQL interpolation, but got `${showCode(other)}`"
            )
        }

      case other =>
        c.abort(
          other.pos,
          s"Expected a CQL string interpolation, but got `${showCode(other)}`"
        )
    }

    val names = bindMarkerNames(c)(params)

    // A constant is the only kind of parameter that can be injected into the query text. The
    // compiler folds those into literals before the macro sees them, which is also why their
    // name is no longer around to build a bind marker out of.
    val injectable = params.map(_.tree match {
      case Literal(Constant(value)) => Some(String.valueOf(value))
      case _ => None
    })

    val bound = boundParams(parts, names, injectable)
    val cql   = interleave(parts, tokensOf(names, injectable, bound))

    val bindParameters = params.indices.collect {
      case idx if bound(idx) => names(idx) -> params(idx)
    }

    cql -> bindParameters
  }

  /** Decides which parameters can be bound, by asking the grammar where a bind marker fits.
    *
    * Every parameter starts out as a bind marker and the statement is handed to the parser as a
    * whole. Whenever the parser rejects one, that parameter is injected into the query text
    * instead and the statement is checked again, until the parser is happy. A parameter the
    * parser rejects and the macro can't inject - anything that isn't a compile-time constant -
    * is left alone, for the caller's validation to report.
    *
    * Each round settles one parameter for good, so this converges in at most one round per
    * parameter.
    *
    * @param parts String Interpolated constant parts
    * @param names bind marker name of each parameter
    * @param injectable query text of each parameter that can be injected as is
    * @return whether each parameter is to be bound
    */
  private def boundParams(
      parts: Seq[String],
      names: Seq[String],
      injectable: Seq[Option[String]]
  ): Seq[Boolean] = {
    val bound = Array.fill(names.size)(true)

    def statement: String = interleave(parts, tokensOf(names, injectable, bound))

    // Where a parameter's text starts within the statement built out of the current decisions
    def startOf(idx: Int): Int =
      parts.take(idx + 1).map(_.length).sum +
        tokensOf(names, injectable, bound).take(idx).map(_.length).sum

    // A bind marker the parser accepted, but which isn't a marker at all: the caller wrapped it
    // in quotes, so it ended up as part of a string literal rather than as a token of its own
    def swallowed(stmt: String): Option[Int] = {
      val markers = CqlValidator.bindMarkerOffsets(stmt)

      names.indices.find(idx => bound(idx) && injectable(idx).isDefined && !markers(startOf(idx)))
    }

    // The rightmost parameter the parser could be rejecting: one that starts no later than the
    // offending token, and that can still be injected instead. Anything further to the right
    // can't be the cause, since parsing stops at the first error.
    def rejected(stmt: String): Option[Int] =
      CqlValidator.firstErrorOffset(stmt).flatMap { offset =>
        names.indices.reverse
          .find(idx => bound(idx) && injectable(idx).isDefined && startOf(idx) <= offset)
      }

    @tailrec
    def settle(rounds: Int): Unit =
      if (rounds > 0) {
        val stmt = statement

        rejected(stmt).orElse(swallowed(stmt)) match {
          case Some(idx) =>
            bound(idx) = false
            settle(rounds - 1)

          // Either the statement is valid, or nothing else can be injected
          case None => ()
        }
      }

    settle(names.size)

    // A statement that can't be made valid is reported with every constant injected as the user
    // wrote it, so that the error points at the query they typed rather than at bind markers
    // they never asked for
    if (CqlValidator.firstErrorOffset(statement).isEmpty) bound.toSeq
    else injectable.map(_.isEmpty)
  }

  /** Name of the bind marker each parameter would use.
    *
    * Parameters that carry a name keep it, which is what makes a statement readable. A constant
    * has been folded into a literal by the time the macro runs, so its name is gone and one is
    * derived from its position instead.
    *
    * @param c Context
    * @param params String Interpolated parameters
    * @return bind marker name of each parameter, in the same order
    */
  private def bindMarkerNames(c: blackbox.Context)(params: Seq[c.Expr[Any]]): Seq[String] = {
    import c.universe._

    val named = params.map(_.tree match {
      case Literal(Constant(_)) => None
      case other => Some(other.symbol.name.toString)
    })

    val taken = named.flatten.toSet

    named.zipWithIndex.map {
      case (Some(name), _) => name

      // Keep suffixing until the derived name can't collide with one of the parameters that
      // came with its own, which would bind both of them to the same marker
      case (None, idx) => Iterator.iterate(s"p$idx")(_ + "_").find(!taken.contains(_)).get
    }
  }

  /** Text each parameter contributes to the query: a bind marker, or the constant it folds to */
  private def tokensOf(
      names: Seq[String],
      injectable: Seq[Option[String]],
      bound: Seq[Boolean]
  ): Seq[String] =
    names.indices.map { idx =>
      // Only a parameter that can be injected is ever left unbound, so the fallback below is
      // there to keep this total rather than because it can be reached
      if (bound(idx)) bindMarker(names(idx))
      else injectable(idx).getOrElse(bindMarker(names(idx)))
    }

  /** A named bind marker, quoted if the name calls for it */
  private def bindMarker(name: String): String =
    if (Strings.needsDoubleQuotes(name)) s":${Strings.doubleQuote(name)}" else s":$name"

  /** Weaves the constant parts of a String Interpolation and its parameters back into a single
    * statement
    */
  private def interleave(parts: Seq[String], params: Seq[String]): String =
    parts
      .zip(params)
      .foldLeft(new StringBuilder()) { case (acc, (part, param)) =>
        acc.append(part).append(param)
      }
      .append(parts.lastOption.getOrElse(""))
      .toString

  /** Sets a Bind Parameter into a CQL BoundStatement
    *
    * @param c Context
    * @param param parameter to set
    * @param bind function called to set the parameter with a given codec
    * @tparam A resulting expression type
    * @return resulting expression
    */
  private def setBindParameter[A](
      c: blackbox.Context
  )(param: c.Expr[Any])(bind: c.Tree => c.Expr[A]): c.Expr[A] = {
    import c.universe._

    // A constant is typed as the single value it holds (eg. `String("helenus")`), and a codec is
    // only ever defined for the type itself, so the singleton has to be widened away first
    val tpe = param.tree.tpe.widen

    c.typecheck(
      q"implicitly[_root_.com.datastax.oss.driver.api.core.`type`.codec.TypeCodec[$tpe]]",
      silent = true
    ) match {
      case EmptyTree =>
        c.abort(
          c.enclosingPosition,
          s"Couldn't find an implicit TypeCodec for [$tpe]"
        )

      case codec =>
        bind(codec)
    }
  }

  // ---------------------------------------------------------------------------
  // Compile-time string evaluation
  // ---------------------------------------------------------------------------

  // Recursively evaluates a compile-time constant String expression, mirroring
  // common constant-folding chains (.stripMargin, .trim, …).
  //
  // The tricky case: String.stripMargin/trim live on StringOps, not String, so
  // the typed tree goes through scala.Predef.augmentString before the method
  // call.  The resulting shape is:
  //   Select(Apply(augmentString, List(lit)), TermName("stripMargin"))
  // rather than Apply(Select(lit, "stripMargin"), Nil).
  // We handle this by (a) recognising the method names as Select nodes and
  // (b) treating any single-arg Apply as a transparent wrapper and recursing
  // into its argument — this covers augmentString and similar identity lifts.
  //
  // Returns Left((reason, pos)) pointing at the *specific* sub-expression that
  // isn't foldable, rather than just failing the whole tree, so the abort
  // message can explain exactly what's wrong and where.
  private def evalString(
      c: blackbox.Context
  )(tree: c.universe.Tree): Either[(String, c.universe.Position), String] = {
    import c.universe._

    tree match {
      case Literal(Constant(s: String)) =>
        Right(s)

      // Zero-arg methods represented as Select (no () in source, typed tree omits Apply)
      case Select(receiver, TermName("stripMargin")) =>
        evalString(c)(receiver).map(_.stripMargin)

      case Select(receiver, TermName("trim")) =>
        evalString(c)(receiver).map(_.trim)

      // Same methods called with explicit ()
      case Apply(Select(receiver, TermName("stripMargin")), Nil) =>
        evalString(c)(receiver).map(_.stripMargin)

      case Apply(Select(receiver, TermName("trim")), Nil) =>
        evalString(c)(receiver).map(_.trim)

      // stripMargin(marginChar)
      case Apply(Select(receiver, TermName("stripMargin")), List(Literal(Constant(ch: Char)))) =>
        evalString(c)(receiver).map(_.stripMargin(ch))

      // String concatenation: `s"...$a..."` is typed as nested `"...".+(a).+("...")` calls
      // rather than a single StringContext(...).s(...) call. Must be handled before the
      // generic single-arg Apply case below, which would otherwise match `+` too and
      // silently discard the receiver (everything concatenated so far).
      // Note: the `+` method name is stored in its encoded form `$plus`, not `+`.
      case Apply(Select(receiver, TermName("$plus")), List(arg)) =>
        for {
          r <- evalString(c)(receiver)
          a <- evalString(c)(arg)
        } yield r + a

      // `StringContext(parts).s(args)`: Scala 2.13 folds a constant interpolation
      // down to a single Literal (handled above), but Scala 2.12 leaves this raw
      // shape in the typed tree. Fold it here when every part and every argument is
      // itself a compile-time constant, so `s"...$const...".toCQL` behaves the same
      // on both versions. Must precede the generic single-arg `Apply` case below,
      // which would otherwise misinterpret a one-argument `.s(arg)`.
      case Apply(Select(Apply(Select(_, TermName("apply")), rawParts), TermName("s")), args) =>
        val partResults = rawParts.map(evalString(c)(_))
        val argResults  = args.map(evalString(c)(_))

        (partResults ++ argResults).collectFirst { case Left(err) => err } match {
          case Some(err) => Left(err)
          case None =>
            val parts  = partResults.collect { case Right(s) => s }
            val values = argResults.collect { case Right(s) => s }
            // n+1 parts interleaved with n argument values: p0 a0 p1 a1 … pn
            Right(parts.head + values.zip(parts.tail).map { case (a, p) => a + p }.mkString)
        }

      // Single-arg application: transparent wrapper (e.g. Predef.augmentString).
      // Recurse into the wrapped value — if it's not a constant we return None.
      case Apply(_, List(inner)) =>
        evalString(c)(inner)

      case other =>
        Left(diagnoseNonConstant(c)(other))
    }
  }

  // Builds a specific, actionable explanation for why a sub-expression isn't a
  // compile-time constant, to replace the generic (and, pre-fix, sometimes
  // misleading) "requires a compile-time constant string" abort.
  private def diagnoseNonConstant(
      c: blackbox.Context
  )(tree: c.universe.Tree): (String, c.universe.Position) = {
    import c.universe._

    tree match {
      case ref @ (Select(_, _) | Ident(_)) if ref.symbol != null && ref.symbol.isTerm =>
        val sym      = ref.symbol.asTerm
        val name     = sym.name.decodedName.toString
        val fullName = sym.fullName

        val reason =
          // A stable (val-like), static (object/singleton member) reference whose type
          // isn't a compile-time constant. This is the "explicit type annotation widens
          // the singleton type away" pitfall (or, less commonly, its initializer isn't
          // itself a literal).
          if (sym.isStable && sym.isStatic)
            s"`$fullName` is a `val`, but its type (`${sym.info}`) isn't a compile-time " +
            "constant. This usually happens when the declaration has an explicit type " +
            s"annotation, e.g. `val $name: ${sym.info} = ...`, which widens it away from " +
            s"its literal singleton type. Remove the annotation, e.g. `final val $name = " +
            s"...`, so scalac can fold it into this string at compile time. If `$name`'s " +
            "value isn't itself a literal (e.g. it's computed), it can never be a " +
            "compile-time constant, regardless of the annotation."
          else if (!sym.isStable)
            s"`$fullName` is a `var`, or the result of a method call (e.g. a `def`), so its " +
            "value is only known at runtime; it can't be a compile-time constant."
          else
            s"`$name` is a runtime value (e.g. a method parameter or local variable), so " +
            "its value can't be known at compile time; it can't be a compile-time constant."

        (reason, ref.pos)

      case other =>
        (
          s"`${showCode(other)}` is not a compile-time constant expression; only string " +
            "literals combined with `+`, `.stripMargin`, or `.trim` are supported.",
          other.pos
        )
    }
  }

  // Returns the position of the innermost string Literal adjusted to the first
  // content character (past the opening quote(s)), used for precise caret placement.
  private def literalPos(
      c: blackbox.Context
  )(tree: c.universe.Tree): Option[c.universe.Position] = {
    import c.universe._
    tree match {
      case Literal(Constant(_: String)) =>
        val p      = tree.pos
        val chars  = p.source.content
        val offset =
          if (
            p.point + 2 < chars.length &&
            chars(p.point) == '"' && chars(p.point + 1) == '"' && chars(p.point + 2) == '"'
          )
            3
          else 1
        Some(p.withPoint(p.point + offset))

      case Select(t, TermName("stripMargin") | TermName("trim")) => literalPos(c)(t)
      case Apply(Select(t, TermName("stripMargin") | TermName("trim")), _) => literalPos(c)(t)
      case Apply(_, List(inner)) => literalPos(c)(inner)
      case _ => None
    }
  }
}
