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

class VectorCodec[T](inner: TypeCodec[T], frozen: Boolean)
    extends AbstractSeqCodec[T, Vector](inner, frozen) {

  override val getJavaType: GenericType[Vector[T]] =
    GenericType
      .of(new TypeToken[Vector[T]]() {}.getType)
      .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
      .asInstanceOf[GenericType[Vector[T]]]

  override def toString: String = s"VectorCodec[${inner.getCqlType.toString}]"
}

object VectorCodec {
  def apply[T](inner: TypeCodec[T], frozen: Boolean): VectorCodec[T] =
    new VectorCodec(inner, frozen)

  def frozen[T](inner: TypeCodec[T]): VectorCodec[T] = VectorCodec[T](inner, frozen = true)
}
