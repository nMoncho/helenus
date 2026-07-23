/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import java.nio.ByteBuffer
import java.time.Duration

import com.datastax.oss.driver.api.core.ConsistencyLevel
import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import org.mockito.ArgumentMatchers._
import org.mockito.MockedConstruction
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StatementOptionsSpec extends AnyWordSpec with Matchers {

  "StatementOptions" should {

    "not apply options if it's 'default'" in {
      val bs      = mock(classOf[BoundStatement])
      val options = StatementOptions.default

      withMockedBuilder(bs) { mocked =>
        options(bs)

        // No builder is created, and the input statement is returned untouched
        mocked.constructed() shouldBe empty
        verifyNoInteractions(bs)
      }
    }

    "apply only set options" in {
      val options = StatementOptions.default.copy(
        bstmtOptions = StatementOptions.default.bstmtOptions.copy(
          routingKeyspace  = Some(CqlIdentifier.fromInternal("foo")),
          timeout          = Some(Duration.ofSeconds(30)),
          consistencyLevel = Some(ConsistencyLevel.ONE)
        )
      )

      val bs = mock(classOf[BoundStatement])

      withMockedBuilder(bs) { mocked =>
        options(bs)

        mocked.constructed() should have size 1
        val builder = mocked.constructed().get(0)

        // Always applied
        verify(builder).setTracing(StatementOptions.default.bstmtOptions.tracing)
        verify(builder).setPageSize(StatementOptions.default.bstmtOptions.pageSize)
        verify(builder).setIdempotence(StatementOptions.default.bstmtOptions.idempotent)

        // Applied because they are 'Some'
        verify(builder).setRoutingKeyspace(any[CqlIdentifier]())
        verify(builder).setTimeout(any())
        verify(builder).setConsistencyLevel(any())

        // Not applied because they are 'None'
        verify(builder, never).setExecutionProfile(any())
        verify(builder, never).setRoutingKey(any[ByteBuffer]())
        verify(builder, never).setPagingState(any[ByteBuffer]())

        verify(builder).build()
      }
    }

  }

  /** Runs `test` with `new BoundStatementBuilder(...)` intercepted by Mockito.
    * The constructed builder is a mock whose fluent setters return the builder
    * itself (so the chained calls in `apply` don't NPE), and whose `build()`
    * returns the supplied statement.
    */
  private def withMockedBuilder(
      built: BoundStatement
  )(test: MockedConstruction[BoundStatementBuilder] => Unit): Unit = {
    val mocked = mockConstruction(
      classOf[BoundStatementBuilder],
      (builder: BoundStatementBuilder, _: MockedConstruction.Context) => {
        when(builder.setTracing(any())).thenReturn(builder)
        when(builder.setPageSize(any())).thenReturn(builder)
        when(builder.setIdempotence(any())).thenReturn(builder)
        when(builder.setExecutionProfile(any())).thenReturn(builder)
        when(builder.setRoutingKeyspace(any[CqlIdentifier]())).thenReturn(builder)
        when(builder.setRoutingKey(any[ByteBuffer]())).thenReturn(builder)
        when(builder.setTimeout(any())).thenReturn(builder)
        when(builder.setPagingState(any[ByteBuffer]())).thenReturn(builder)
        when(builder.setConsistencyLevel(any())).thenReturn(builder)
        when(builder.build()).thenReturn(built)
      }
    )

    try test(mocked)
    finally mocked.close()
  }

}
