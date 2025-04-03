/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec.udt

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.UserDefinedType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import net.nmoncho.helenus.models.Address
import net.nmoncho.helenus.models.Hotel
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UnifiedUdtCodecInteractionSpec extends AnyWordSpec with Matchers {

  "UnifiedUdtCodec" should {
    "delegate calls" in {
      val identical    = mockCodec[Address]()
      val nonIdentical = mockCodecFn[Address]()
      val udt          = mock(classOf[UserDefinedType])

      val codec = new UnifiedUDTCodec(identical, nonIdentical)

      assertInteractions(
        codec,
        underlying = identical,
        other      = nonIdentical(udt)
      )

      codec.adapt(udt)
      clearInvocations(identical)

      // swap test values
      assertInteractions(
        codec,
        underlying = nonIdentical(udt),
        other      = identical
      )
    }
  }

  private def assertInteractions(
      codec: UnifiedUDTCodec[Address],
      underlying: TypeCodec[Address],
      other: TypeCodec[Address]
  ): Unit = {
    withClue("on `encode` and `decode` calls") {
      codec.encode(Address("", "", "", "", ""), ProtocolVersion.DEFAULT)
      verify(underlying, atMostOnce()).encode(any(), any())
      verify(other, never()).encode(any(), any())

      codec.decode(ByteBuffer.wrap(Array.emptyByteArray), ProtocolVersion.DEFAULT)
      verify(underlying, atMostOnce()).decode(any(), any())
      verify(other, never()).decode(any(), any())
    }

    withClue("on `format` and `parse` calls") {
      codec.format(Address("", "", "", "", ""))
      verify(underlying, atMostOnce()).format(any())
      verify(other, never()).format(any())

      codec.parse("")
      verify(underlying, atMostOnce()).parse(any())
      verify(other, never()).parse(any())
    }

    withClue("on `getJavaType` and `getCqlType` calls") {
      codec.getJavaType()
      verify(underlying, atMostOnce()).getJavaType()
      verify(other, never()).getJavaType()

      codec.getCqlType()
      verify(underlying, atMostOnce()).getJavaType()
      verify(other, never()).getJavaType()
    }

    withClue("on `accepts` calls") {
      codec.accepts(mock(classOf[GenericType[Address]]))
      verify(underlying, atMostOnce()).accepts(any[GenericType[_]])
      verify(other, never()).accepts(any[GenericType[_]])

      codec.accepts(DataTypes.TEXT)
      // using atLeastOnce since after adaptation we've 2 instead of 1, like the others
      verify(underlying, atLeastOnce()).accepts(any[DataType])
      verify(other, never()).accepts(any[DataType])

      codec.accepts(classOf[Hotel])
      verify(underlying, atMostOnce()).accepts(any[Class[_]])
      verify(other, never()).accepts(any[Class[_]])

      codec.accepts("")
      verify(underlying, atMostOnce()).accepts(any[java.lang.Object])
      verify(other, never()).accepts(any[java.lang.Object])
    }
  }

  private def mockCodec[A](): TypeCodec[A] = {
    val genType = mock(classOf[GenericType[A]])
    val udtType = mock(classOf[UserDefinedType])
    val codec   = mock(classOf[TypeCodec[A]])

    when(codec.encode(any(), any())).thenReturn(ByteBuffer.wrap(Array.emptyByteArray))
    when(codec.decode(any(), any())).thenReturn(null.asInstanceOf[A])

    when(codec.getJavaType).thenReturn(genType)
    when(codec.getCqlType).thenReturn(udtType)

    when(codec.parse(any())).thenReturn(null.asInstanceOf[A])
    when(codec.format(any())).thenReturn("")

    when(codec.accepts(any[GenericType[_]])).thenReturn(false)
    when(codec.accepts(any[DataType])).thenReturn(false)
    when(codec.accepts(any[Class[_]])).thenReturn(false)
    when(codec.accepts(any[java.lang.Object])).thenReturn(false)

    codec
  }

  private def mockCodecFn[A](): UserDefinedType => TypeCodec[A] = {
    val codec = mockCodec[A]()

    _ => codec
  }
}
