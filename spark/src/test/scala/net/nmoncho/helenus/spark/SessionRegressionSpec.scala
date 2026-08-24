/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** the Spark module must never open its own [[com.datastax.oss.driver.api.core.CqlSession]].
  *
  * It borrows the connector's session through `CassandraConnector.withSessionDo`
  * (centralized in `net.nmoncho.helenus.spark.sink.CassandraSessions`), inheriting the
  * connector's pooling, retry, and topology handling instead of opening a competing
  * session per partition.
  *
  * This is a structural regression guard over the module's own main sources, so it
  * cannot silently regress as the write path grows. The end-to-end runtime assertion
  * that the write path fails cleanly when no connector config is present, precisely
  * because it never builds a session of its own.
  */
final class SessionRegressionSpec extends AnyWordSpec with Matchers {

  private def scalaFilesUnder(dir: File): Seq[File] =
    if (dir.isDirectory) Option(dir.listFiles()).toSeq.flatten.flatMap(scalaFilesUnder)
    else if (dir.getName.endsWith(".scala")) Seq(dir)
    else Seq.empty

  // Forked tests run with the module base as the working directory (`src/...`); the
  // aggregate/root path (`spark/src/...`) is the fallback when they do not.
  private def mainSources: Seq[File] = {
    val candidates = Seq(new File("src/main/scala"), new File("spark/src/main/scala"))
    val root       = candidates
      .find(_.isDirectory)
      .getOrElse(
        fail(s"could not locate the spark main sources; tried ${candidates.mkString(", ")}")
      )

    scalaFilesUnder(root)
  }

  private def contentOf(file: File): String =
    new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)

  "The Spark module main sources" should {
    "never build a CqlSession themselves (no CqlSessionBuilder)" in {
      val forbidden = Seq("CqlSessionBuilder", "CqlSession.builder")

      val offenders = mainSources.flatMap { file =>
        val content = contentOf(file)
        forbidden.collect { case token if content.contains(token) => s"${file.getPath} -> $token" }
      }

      withClue(
        "Sessions must come from CassandraConnector.withSessionDo, not a self-built session: "
      ) {
        offenders shouldBe empty
      }
    }

    "route session access through the connector's withSessionDo" in {
      val helper = mainSources
        .find(_.getName == "CassandraSessions.scala")
        .getOrElse(fail("expected CassandraSessions.scala to centralize Spark session access"))

      contentOf(helper) should include("withSessionDo")
    }
  }
}
