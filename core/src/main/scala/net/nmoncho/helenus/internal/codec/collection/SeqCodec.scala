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

class SeqCodec[T](inner: TypeCodec[T], frozen: Boolean)
    extends AbstractSeqCodec[T, Seq](inner, frozen) {

  override val getJavaType: GenericType[Seq[T]] =
    GenericType
      .of(new TypeToken[Seq[T]]() {}.getType)
      .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
      .asInstanceOf[GenericType[Seq[T]]]

  override def toString: String = s"SeqCodec[${inner.getCqlType.toString}]"
}

object SeqCodec {
  def apply[T](inner: TypeCodec[T], frozen: Boolean): SeqCodec[T] =
    new SeqCodec(inner, frozen)

  def frozen[T](inner: TypeCodec[T]): SeqCodec[T] = SeqCodec[T](inner, frozen = true)
}
