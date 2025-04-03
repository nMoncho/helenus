/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.internal.codec.collection

import scala.collection.immutable.SortedSet

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import com.datastax.oss.driver.api.core.`type`.reflect.GenericTypeParameter
import com.datastax.oss.driver.shaded.guava.common.reflect.TypeToken

class SortedSetCodec[T: Ordering](inner: TypeCodec[T], frozen: Boolean)
    extends AbstractSetCodec[T, SortedSet](inner, frozen) {

  override val getJavaType: GenericType[SortedSet[T]] =
    GenericType
      .of(new TypeToken[SortedSet[T]]() {}.getType)
      .where(new GenericTypeParameter[T] {}, inner.getJavaType.wrap())
      .asInstanceOf[GenericType[SortedSet[T]]]

  override def toString: String = s"SortedSetCodec[${inner.getCqlType.toString}]"
}

object SortedSetCodec {
  def apply[T: Ordering](inner: TypeCodec[T], frozen: Boolean): SortedSetCodec[T] =
    new SortedSetCodec(inner, frozen)

  def frozen[T: Ordering](inner: TypeCodec[T]): SortedSetCodec[T] =
    SortedSetCodec[T](inner, frozen = true)
}
