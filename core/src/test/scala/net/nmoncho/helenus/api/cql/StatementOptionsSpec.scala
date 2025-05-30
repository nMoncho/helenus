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
import com.datastax.oss.driver.api.core.cql.PagingState
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StatementOptionsSpec extends AnyWordSpec with Matchers {

  "StatementOptions" should {

    "not apply options if it's 'default'" in {
      // test setup
      val bs      = mockBoundStatement()
      val options = StatementOptions.default

      // test execution
      options(bs)

      // test assertion
      verifyNoInteractions(bs)
    }

    "apply only set options" in {
      // test setup
      val bs      = mockBoundStatement()
      val options = StatementOptions.default.copy(
        bstmtOptions = StatementOptions.default.bstmtOptions.copy(
          routingKeyspace  = Some(CqlIdentifier.fromInternal("foo")),
          timeout          = Some(Duration.ofSeconds(30)),
          consistencyLevel = Some(ConsistencyLevel.ONE)
        )
      )

      // test execution
      options(bs)

      // test assertion
      verify(bs, atMostOnce).setTracing(StatementOptions.default.bstmtOptions.tracing)
      verify(bs, atMostOnce).setPageSize(StatementOptions.default.bstmtOptions.pageSize)
      verify(bs, atMostOnce).setRoutingKey(any())
      verify(bs, atMostOnce).setTimeout(any())
      verify(bs, atMostOnce).setConsistencyLevel(any())

      verify(bs, never).setExecutionProfile(any())
      verify(bs, never).setRoutingKey(any())
      verify(bs, never).setPagingState(any[ByteBuffer]())
      verify(bs, never).setPagingState(any[PagingState]())
    }

  }

  private def mockBoundStatement(): BoundStatement = {
    val bs = mock(classOf[BoundStatement])

    when(bs.setTracing(any())).thenReturn(bs)
    when(bs.setPageSize(any())).thenReturn(bs)
    when(bs.setExecutionProfile(any())).thenReturn(bs)
    when(bs.setRoutingKeyspace(any[CqlIdentifier]())).thenReturn(bs)
    when(bs.setRoutingKey(any())).thenReturn(bs)
    when(bs.setTimeout(any())).thenReturn(bs)
    when(bs.setPagingState(any[ByteBuffer]())).thenReturn(bs)
    when(bs.setConsistencyLevel(any())).thenReturn(bs)

    bs
  }

}
