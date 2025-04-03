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

class ListCodec[T](inner: TypeCodec[T], frozen: Boolean)
    extends AbstractSeqCodec[T, List](inner, frozen) {

  override val getJavaType: GenericType[List[T]] =
    GenericType
      .of(new TypeToken[List[T]]() {}.getType)
      .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
      .asInstanceOf[GenericType[List[T]]]

  override def toString: String = s"ListCodec[${inner.getCqlType.toString}]"
}

object ListCodec {
  def apply[T](inner: TypeCodec[T], frozen: Boolean): ListCodec[T] =
    new ListCodec(inner, frozen)

  def frozen[T](inner: TypeCodec[T]): ListCodec[T] = ListCodec[T](inner, frozen = true)
}
