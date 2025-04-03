/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

object BigDecimalCodec
    extends MappingCodec[java.math.BigDecimal, BigDecimal](
      TypeCodecs.DECIMAL,
      GenericType.of(classOf[BigDecimal])
    ) {

  override def innerToOuter(value: java.math.BigDecimal): BigDecimal =
    if (value == null) null else BigDecimal(value)

  override def outerToInner(value: BigDecimal): java.math.BigDecimal =
    if (value == null) null else value.bigDecimal

}
