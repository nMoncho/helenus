/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.enums

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import enumeratum.Enum
import enumeratum.EnumEntry

/** [[com.datastax.oss.driver.api.core.`type`.codec.TypeCodec]] for an Enumeratum [[Enum]] mapped to
  * its Int position (the index in `values`).
  * This class won't be made available as implicit, since there is another possible representation for enums
  *
  * @param enumeratum enumeratum to map to an int
  */
class EnumeratumOrdinalCodec[A <: EnumEntry](enumeratum: Enum[A])(implicit tag: ClassTag[A])
    extends MappingCodec[java.lang.Integer, A](
      TypeCodecs.INT,
      GenericType.of(tag.runtimeClass.asInstanceOf[Class[A]])
    ) {

  override def innerToOuter(value: java.lang.Integer): A =
    if (value == null) null.asInstanceOf[A] else enumeratum.values(value)

  override def outerToInner(value: A): java.lang.Integer =
    if (value == null) null
    else {
      // `Enum.indexOf` returns -1 (rather than throwing) for an entry not in `values`.
      // Fail loudly on encode instead of silently writing -1, which would only blow up
      // as an `IndexOutOfBoundsException` on a later decode.
      val index = enumeratum.indexOf(value)
      require(index >= 0, s"$value is not a member of the enum being encoded")
      index
    }

  override def toString: String = s"EnumeratumOrdinalCodec[${tag.runtimeClass.getSimpleName}]"
}
