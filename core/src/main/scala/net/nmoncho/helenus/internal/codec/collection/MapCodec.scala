/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.collection

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.reflect.GenericTypeParameter
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

class MapCodec[K, V](keyInner: TypeCodec[K], valueInner: TypeCodec[V], frozen: Boolean)
    extends AbstractMapCodec[K, V, Map](keyInner, valueInner, frozen) {

  override val getJavaType: GenericType[Map[K, V]] =
    GenericType
      .of(new TypeToken[Map[K, V]]() {}.getType)
      .where(new GenericTypeParameter[K] {}, keyInner.getJavaType.wrap())
      .where(new GenericTypeParameter[V] {}, valueInner.getJavaType.wrap())
      .asInstanceOf[GenericType[Map[K, V]]]

  override def toString: String =
    s"MapCodec[${keyInner.getCqlType.toString}, ${valueInner.getCqlType.toString}]"
}

object MapCodec {
  def apply[K, V](
      keyInner: TypeCodec[K],
      valueInner: TypeCodec[V],
      frozen: Boolean
  ): MapCodec[K, V] =
    new MapCodec(keyInner, valueInner, frozen)

  def frozen[K, V](keyInner: TypeCodec[K], valueInner: TypeCodec[V]): MapCodec[K, V] =
    MapCodec(keyInner, valueInner, frozen = true)
}
