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
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction

package object sink {

  /** Transforms a [[ScalaPreparedStatement]] into a Sink of type [[SinkFunction]]
    *
    * @param pstmtBuilder function taking a [[CqlSession]] and providing a [[ScalaPreparedStatement]]
    * @param config       cassandra configuration
    * @tparam T input type for the [[ScalaPreparedStatement]]
    * @tparam Out
    * @return [[SinkFunction]]
    */
  def asSinkFunction[T, Out](
      pstmtBuilder: CqlSession => ScalaPreparedStatement[T, Out],
      config: CassandraSink.Config
  ): SinkFunction[T] = new RichSinkFunction[T] /*with CheckpointedFunction*/ {

    private val semaphore                             = new Semaphore(config.maxConcurrentRequests)
    private var session: CqlSession                   = _
    private var pstmt: ScalaPreparedStatement[T, Out] = _

    override def invoke(value: T): Unit = {
      tryAcquire(1)

      session.executeAsync(pstmt.tupled(value)).whenComplete { (_, throwable) =>
        if (throwable != null) {
          CassandraSink.log.error("Error while sending value.", throwable)
          config.failureHandler(throwable)
        }

        semaphore.release()
      }
    }

    override def open(configuration: Configuration): Unit = {
      super.open(configuration)
      session = config.session()

      pstmt = pstmtBuilder(session)
    }

    override def close(): Unit = {
      flush()
      session.close()
    }

    private def tryAcquire(permits: Int): Unit =
      SinkUtils.tryAcquire(
        permits,
        config.maxConcurrentRequests,
        config.maxConcurrentRequestsTimeout,
        semaphore
      )

    private def flush(): Unit = {
      tryAcquire(config.maxConcurrentRequests)
      semaphore.release(config.maxConcurrentRequests)
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
