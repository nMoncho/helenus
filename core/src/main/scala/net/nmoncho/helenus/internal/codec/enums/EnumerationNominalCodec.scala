/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.enums

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs

/** [[com.datastax.oss.driver.api.core.`type`.codec.TypeCodec]] for an [[Enumeration]] mapped to its String representation.
  * This class won't be made available as implicit, since there is another possible representation for enums
  *
  * @param enumeration enumeration to map to a string
  */
class EnumerationNominalCodec[T <: Enumeration](enumeration: T)
    extends MappingCodec[String, T#Value](
      TypeCodecs.stringCodec,
      GenericType.of(classOf[T#Value])
    ) {

  override def innerToOuter(value: String): T#Value =
    if (value == null) null else enumeration.withName(value)

  override def outerToInner(value: T#Value): String =
    if (value == null) null else value.toString

  override def toString: String = s"EnumerationNominalCodec[${enumeration.toString}]"
}
