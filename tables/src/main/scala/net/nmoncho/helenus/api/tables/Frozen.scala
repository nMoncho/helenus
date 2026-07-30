/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.tables

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.DataType
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType

/** Marks a collection field as CQL `frozen<...>`: the whole collection is
  * serialized as a single value, so unlike a regular collection it has no
  * per-element `CONTAINS` / `CONTAINS KEY` (the `Column[Frozen[_]]` methods
  * for those simply don't type-check, since `Frozen[T]` is not itself an
  * `Iterable` / `Map`), and a secondary index on it must be a `FULL` index
  * (see [[IndexTargets]]).
  *
  * Wrap the field's type in the mapped case class, e.g.
  * `case class Article(id: UUID, tags: Frozen[Set[String]])`, and declare the
  * column with `Table.frozenColumn`, which only needs the wrapped type:
  * `frozenColumn[Set[String]]("tags")` (equivalent to, but shorter than,
  * `column[Frozen[Set[String]]]("tags")`).
  */
final case class Frozen[T](value: T)

object Frozen {
  implicit def frozenTypeCodec[T](implicit codec: TypeCodec[T]): TypeCodec[Frozen[T]] =

    new TypeCodec[Frozen[T]] {
      override def getJavaType: GenericType[Frozen[T]] =
        codec.getJavaType.asInstanceOf[GenericType[Frozen[T]]]

      override def getCqlType: DataType = codec.getCqlType

      override def encode(value: Frozen[T], protocolVersion: ProtocolVersion): ByteBuffer =
        codec.encode(value.value, protocolVersion)

      override def decode(bytes: ByteBuffer, protocolVersion: ProtocolVersion): Frozen[T] =
        Frozen(codec.decode(bytes, protocolVersion))

      override def format(value: Frozen[T]): String = codec.format(value.value)

      override def parse(value: String): Frozen[T] = Frozen(codec.parse(value))
    }
}
