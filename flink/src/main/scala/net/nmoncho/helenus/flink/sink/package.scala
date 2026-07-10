/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink

import java.util.concurrent.CompletionStage
import java.util.concurrent.Semaphore

import com.datastax.oss.driver.api.core.CqlSession
import net.nmoncho.helenus.api.cql.ScalaPreparedStatement
import org.apache.flink.api.common.io.OutputFormatBase
import org.apache.flink.api.common.io.SinkUtils
import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.api.connector.sink2.SinkWriter
import org.apache.flink.api.connector.sink2.WriterInitContext
import org.apache.flink.configuration.Configuration

package object sink {

  /** Transforms a [[ScalaPreparedStatement]] into a Sink of type [[Sink]]
    *
    * @param pstmtBuilder function taking a [[CqlSession]] and providing a [[ScalaPreparedStatement]]
    * @param config       cassandra configuration
    * @tparam T input type for the [[ScalaPreparedStatement]]
    * @tparam Out
    * @return [[Sink]]
    */
  def asSink[T, Out](
      pstmtBuilder: CqlSession => ScalaPreparedStatement[T, Out],
      config: CassandraSink.Config
  ): Sink[T] = new Sink[T] {

    override def createWriter(context: WriterInitContext): SinkWriter[T] = new SinkWriter[T] {

      private val semaphore           = new Semaphore(config.maxConcurrentRequests)
      private val session: CqlSession = config.session()
      private val pstmt: ScalaPreparedStatement[T, Out] = pstmtBuilder(session)

      override def write(element: T, context: SinkWriter.Context): Unit = {
        tryAcquire(1)

        session.executeAsync(pstmt.tupled(element)).whenComplete { (_, throwable) =>
          if (throwable != null) {
            CassandraSink.log.error("Error while sending value.", throwable)
            config.failureHandler(throwable)
          }

          semaphore.release()
        }
        ()
      }

      override def flush(endOfInput: Boolean): Unit = {
        tryAcquire(config.maxConcurrentRequests)
        semaphore.release(config.maxConcurrentRequests)
      }

      override def close(): Unit = {
        flush(endOfInput = true)
        session.close()
      }

      private def tryAcquire(permits: Int): Unit =
        SinkUtils.tryAcquire(
          permits,
          config.maxConcurrentRequests,
          config.maxConcurrentRequestsTimeout,
          semaphore
        )
    }
  }

  /** Transforms a [[ScalaPreparedStatement]] into a Sink of type [[OutputFormatBase]]
    *
    * @param pstmtBuilder function taking a [[CqlSession]] and providing a [[ScalaPreparedStatement]]
    * @param config cassandra configuration
    * @tparam T input type for the [[ScalaPreparedStatement]]
    * @tparam Out
    * @return [[OutputFormatBase]]
    */
  def asOutputFormat[T, Out](
      pstmtBuilder: CqlSession => ScalaPreparedStatement[T, Out],
      config: CassandraSink.Config
  ): OutputFormatBase[T, Unit] =
    new OutputFormatBase[T, Unit](
      config.maxConcurrentRequests,
      config.maxConcurrentRequestsTimeout
    ) {

      private var session: CqlSession                   = _
      private var pstmt: ScalaPreparedStatement[T, Out] = _

      override def send(record: T): CompletionStage[Unit] = {
        val result: CompletionStage[Unit] =
          session.executeAsync(pstmt.tupled(record)).thenApply(_ => ())

        result.whenComplete { (_, throwable) =>
          if (throwable != null) {
            CassandraSink.log.error("Error while sending value.", throwable)
            config.failureHandler(throwable)
          }
        }
      }

      override def configure(parameters: Configuration): Unit = ()

      override def postOpen(): Unit = {
        super.postOpen()
        session = config.session()
        pstmt   = pstmtBuilder(session)
      }

      override def postClose(): Unit = {
        super.postClose()
        session.close()
      }
    }
}
