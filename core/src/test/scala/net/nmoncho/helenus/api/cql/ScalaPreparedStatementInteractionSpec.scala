/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.cql

import java.nio.ByteBuffer
import java.util.Collections

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.cql.EmptyColumnDefinitions
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs
import net.nmoncho.helenus.internal.cql.ScalaPreparedStatement1
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ScalaPreparedStatementInteractionSpec extends AnyWordSpec with Matchers {

  private val id         = ByteBuffer.wrap("pstmt-id".getBytes)
  private val metadataId = ByteBuffer.wrap("metadata-id".getBytes())

  "ScalaPreparedStatement" should {
    "delegate method calls" in {
      val (spstmt, pstmt) = mockScalaPstmt[String](TypeCodecs.stringCodec)

      // trigger interactions
      spstmt.getId shouldBe id
      spstmt.getResultMetadataId shouldBe metadataId

      spstmt.getQuery shouldBe "some-query"

      spstmt.getVariableDefinitions shouldBe EmptyColumnDefinitions.INSTANCE
      spstmt.getResultSetDefinitions shouldBe EmptyColumnDefinitions.INSTANCE
      spstmt.getPartitionKeyIndices shouldBe a[java.util.List[Integer]]

      spstmt.bind("foo") shouldBe a[BoundStatement]
      spstmt.boundStatementBuilder("foo") shouldBe a[BoundStatementBuilder]

      spstmt.setResultMetadata(id, EmptyColumnDefinitions.INSTANCE)

      // verify interaction
      verify(pstmt, atLeastOnce()).getId
      verify(pstmt, atLeastOnce()).getResultMetadataId

      verify(pstmt, atLeastOnce()).getQuery

      verify(pstmt, atLeastOnce()).getVariableDefinitions
      verify(pstmt, atLeastOnce()).getResultSetDefinitions
      verify(pstmt, atLeastOnce()).getPartitionKeyIndices

      verify(pstmt, atLeastOnce()).bind("foo")
      verify(pstmt, atLeastOnce()).boundStatementBuilder("foo")

      verify(pstmt, atLeastOnce()).setResultMetadata(id, EmptyColumnDefinitions.INSTANCE)
    }
  }

  private def mockScalaPstmt[U](
      codec: TypeCodec[U]
  ): (ScalaPreparedStatement[U, Row], PreparedStatement) = {
    val pstmt = mockPstmt

    new ScalaPreparedStatement1[U, Row](
      pstmt,
      RowMapper.identity,
      StatementOptions.default,
      codec
    ) -> pstmt
  }

  private def mockPstmt: PreparedStatement = {
    val pstmt = mock(classOf[PreparedStatement])

    when(pstmt.getId).thenReturn(id)
    when(pstmt.getResultMetadataId).thenReturn(metadataId)

    when(pstmt.getQuery).thenReturn("some-query")
    when(pstmt.getVariableDefinitions).thenReturn(EmptyColumnDefinitions.INSTANCE)
    when(pstmt.getResultSetDefinitions).thenReturn(EmptyColumnDefinitions.INSTANCE)
    when(pstmt.getPartitionKeyIndices).thenReturn(
      Collections.singletonList(1): java.util.List[Integer]
    )

    when(pstmt.bind(any())).thenReturn(mock(classOf[BoundStatement]))
    when(pstmt.boundStatementBuilder(any())).thenReturn(mock(classOf[BoundStatementBuilder]))

    pstmt
  }
}
