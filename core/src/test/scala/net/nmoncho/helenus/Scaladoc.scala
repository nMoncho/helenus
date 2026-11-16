/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files

import scala.tools.nsc.doc.DocFactory
import scala.tools.nsc.doc.Settings
import scala.tools.nsc.reporters.ConsoleReporter

/** Runs Scaladoc over a snippet, on the test classpath.
  *
  * Scaladoc's typer doesn't fold a `final val` reference into a literal (it keeps the name for
  * the docs), so macros see `Select(Metrics, tableName)` typed `String("metrics")` instead
  * of `Literal("metrics")`. `sbt compile` passing says nothing about `sbt doc`.
  */
object Scaladoc {

  private val classpath = Iterator
    .iterate(getClass.getClassLoader)(_.getParent)
    .takeWhile(_ != null)
    .collect { case u: java.net.URLClassLoader => u.getURLs }
    .flatten
    .map(_.getFile)
    .mkString(java.io.File.pathSeparator)

  /** Error output of documenting `code`, empty when it succeeds. */
  def errorsOf(code: String): String = {
    val dir  = Files.createTempDirectory("helenus-scaladoc")
    val file = dir.resolve("Snippet.scala")
    Files.write(file, code.getBytes("UTF-8"))

    val out      = new StringWriter()
    val settings = new Settings(msg => out.write(msg))
    settings.classpath.value = classpath
    settings.outdir.value    = dir.toString

    val reporter =
      new ConsoleReporter(settings, new BufferedReader(new StringReader("")), new PrintWriter(out))
    new DocFactory(reporter, settings).document(List(file.toString))

    if (reporter.hasErrors) out.toString else ""
  }
}
