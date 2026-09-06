/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.enums

import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.`type`.codec.MappingCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import enumeratum.Enum
import enumeratum.EnumEntry
import net.nmoncho.helenus.api.`type`.codec.TypeCodecs

/** [[com.datastax.oss.driver.api.core.`type`.codec.TypeCodec]] for an Enumeratum [[Enum]] mapped to
  * its `entryName` String representation.
  * This class won't be made available as implicit, since there is another possible representation for enums
  *
  * @param enumeratum enumeratum to map to a string
  */
class EnumeratumNominalCodec[A <: EnumEntry](enumeratum: Enum[A])(implicit tag: ClassTag[A])
    extends MappingCodec[String, A](
      TypeCodecs.stringCodec,
      GenericType.of(tag.runtimeClass.asInstanceOf[Class[A]])
    ) {

  override def innerToOuter(value: String): A =
    if (value == null) null.asInstanceOf[A] else enumeratum.withName(value)

  override def outerToInner(value: A): String =
    if (value == null) null else value.entryName

  override def toString: String = s"EnumeratumNominalCodec[${tag.runtimeClass.getSimpleName}]"
}
