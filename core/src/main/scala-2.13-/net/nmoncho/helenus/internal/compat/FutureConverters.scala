/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.compat

import java.util.concurrent.CompletionStage

import scala.concurrent.Future

object FutureConverters {

  def asScala[T](cs: CompletionStage[T]): Future[T] =
    cs.asScala

  def asJava[T](f: Future[T]): CompletionStage[T] =
    f.asJava

  implicit class CompletionStageOps[T](private val cs: CompletionStage[T]) extends AnyVal {
    def asScala: Future[T] = scala.compat.java8.FutureConverters.toScala(cs)
  }

  implicit class FutureOps[T](private val f: Future[T]) extends AnyVal {
    def asJava: CompletionStage[T] = scala.compat.java8.FutureConverters.toJava(f)
  }

}
