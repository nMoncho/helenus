/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.enums

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

/** [[com.datastax.oss.driver.api.core.`type`.codec.TypeCodec]] for an [[Enumeration]] mapped to its Int representation.
  * This class won't be made available as implicit, since there is another possible representation for enums
  *
  * @param enumeration enumeration to map to an int
  */
class EnumerationOrdinalCodec[T <: Enumeration](enumeration: T)
    extends MappingCodec[java.lang.Integer, T#Value](
      TypeCodecs.INT,
      GenericType.of(classOf[T#Value])
    ) {

  override def innerToOuter(value: java.lang.Integer): T#Value =
    if (value == null) null else enumeration(value)

  override def outerToInner(value: T#Value): java.lang.Integer =
    if (value == null) null else value.id

  override def toString: String = s"EnumerationOrdinalCodec[${enumeration.toString}]"
}
