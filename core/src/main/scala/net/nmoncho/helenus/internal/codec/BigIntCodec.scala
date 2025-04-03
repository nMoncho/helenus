/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

object BigIntCodec
    extends MappingCodec[java.math.BigInteger, BigInt](
      TypeCodecs.VARINT,
      GenericType.of(classOf[BigInt])
    ) {

  override def innerToOuter(value: java.math.BigInteger): BigInt =
    if (value == null) null else BigInt(value)

  override def outerToInner(value: BigInt): java.math.BigInteger =
    if (value == null) null else value.bigInteger

}
