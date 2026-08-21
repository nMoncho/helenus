/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.macros

import scala.annotation.unused
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.macros.blackbox

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement.CQLQuery
import net.nmoncho.helenus.api.cql.WrappedBoundStatement
import net.nmoncho.helenus.internal.cql.BindInference
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
  )(params: c.Expr[Any]*)(session: c.Expr[CqlSession]): c.Expr[WrappedBoundStatement[Row]] =
    cqlImpl(c)(params: _*)(session, validate = true)

  /** `unsafeCql"..."`: like `cql"..."` but skips the compile-time syntactic check
    * Use it when an interpolated query is valid CQL the checker wrongly rejects (a
    * newer-/DSE-only feature, or a known grammar gap) so it need not be hand-built as a string. The
    * bind-versus-inject machinery is kept, so parameters are still bound or injected as usual.
    */
  def unsafeCql(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(session: c.Expr[CqlSession]): c.Expr[WrappedBoundStatement[Row]] =
    cqlImpl(c)(params: _*)(session, validate = false)

  private def cqlImpl(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[CqlSession],
      validate: Boolean
  ): c.Expr[WrappedBoundStatement[Row]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    if (validate) validateOrAbort(c)(stmt)

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
  ): c.Expr[Future[WrappedBoundStatement[Row]]] =
    cqlAsyncImpl(c)(params: _*)(session, ec, validate = true)

  /** Async counterpart of [[unsafeCql]]: `unsafeCqlAsync"..."` skips validation. */
  def unsafeCqlAsync(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[Future[CqlSession]],
      ec: c.Expr[ExecutionContext]
  ): c.Expr[Future[WrappedBoundStatement[Row]]] =
    cqlAsyncImpl(c)(params: _*)(session, ec, validate = false)

  private def cqlAsyncImpl(
      c: blackbox.Context
  )(params: c.Expr[Any]*)(
      session: c.Expr[Future[CqlSession]],
      ec: c.Expr[ExecutionContext],
      validate: Boolean
  ): c.Expr[Future[WrappedBoundStatement[Row]]] = {
    import c.universe._

    val (stmt, bindParameters) = buildStatement(c)(params)

    if (validate) validateOrAbort(c)(stmt)

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

  /** Runs the compile-time syntactic check, aborting with a positioned message on failure. */
  private def validateOrAbort(c: blackbox.Context)(stmt: String): Unit =
    CqlValidator.validate(stmt) match {
      case Right(_) => // do nothing, all good
      case Left((error, pos)) =>
        c.abort(
          c.enclosingPosition.withPoint(c.enclosingPosition.point + pos),
          s"Invalid CQL [$stmt]. Cause: $error"
        )
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

    val bound = BindInference.boundParams(parts, names, injectable)
    val cql   = BindInference.interleave(parts, BindInference.tokensOf(names, injectable, bound))

    val bindParameters = params.indices.collect {
      case idx if bound(idx) => names(idx) -> params(idx)
    }

    cql -> bindParameters
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
