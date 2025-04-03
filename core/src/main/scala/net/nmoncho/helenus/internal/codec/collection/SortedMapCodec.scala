/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.collection

import scala.collection.immutable.SortedMap

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.reflect.GenericTypeParameter
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

class SortedMapCodec[K: Ordering, V](
    keyInner: TypeCodec[K],
    valueInner: TypeCodec[V],
    frozen: Boolean
) extends AbstractMapCodec[K, V, SortedMap](keyInner, valueInner, frozen) {

  override val getJavaType: GenericType[SortedMap[K, V]] =
    GenericType
      .of(new TypeToken[SortedMap[K, V]]() {}.getType)
      .where(new GenericTypeParameter[K] {}, keyInner.getJavaType.wrap())
      .where(new GenericTypeParameter[V] {}, valueInner.getJavaType.wrap())
      .asInstanceOf[GenericType[SortedMap[K, V]]]

  override def toString: String =
    s"SortedMapCodec[${keyInner.getCqlType.toString}, ${valueInner.getCqlType.toString}]"
}

object SortedMapCodec {
  def apply[K: Ordering, V](
      keyInner: TypeCodec[K],
      valueInner: TypeCodec[V],
      frozen: Boolean
  ): SortedMapCodec[K, V] =
    new SortedMapCodec(keyInner, valueInner, frozen)

  def frozen[K: Ordering, V](
      keyInner: TypeCodec[K],
      valueInner: TypeCodec[V]
  ): SortedMapCodec[K, V] =
    SortedMapCodec(keyInner, valueInner, frozen = true)
}
