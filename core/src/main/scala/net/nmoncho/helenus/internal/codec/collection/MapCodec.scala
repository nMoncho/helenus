/*
 * Copyright (c) 2021 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.nmoncho.helenus.internal.codec.collection

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.{ GenericType, GenericTypeParameter }
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
