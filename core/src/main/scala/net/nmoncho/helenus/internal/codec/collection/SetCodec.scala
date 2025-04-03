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

class SetCodec[T](inner: TypeCodec[T], frozen: Boolean)
    extends AbstractSetCodec[T, Set](inner, frozen) {

  override val getJavaType: GenericType[Set[T]] =
    GenericType
      .of(new TypeToken[Set[T]]() {}.getType)
      .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
      .asInstanceOf[GenericType[Set[T]]]

  override def toString: String = s"SetCodec[${inner.getCqlType.toString}]"
}

object SetCodec {
  def apply[T](inner: TypeCodec[T], frozen: Boolean): SetCodec[T] = new SetCodec[T](inner, frozen)

  def frozen[T](inner: TypeCodec[T]): SetCodec[T] = SetCodec[T](inner, frozen = true)
}
