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

package net.nmoncho.helenus.flink.typeinfo
package collection

import org.apache.flink.api.common.typeinfo.TypeInformation

package object immutable {

  class ListTypeInformation[T](inner: TypeInformation[T])
      extends IterableTypeInformation[T, List](inner) {

    override def toString(): String = s"List[${inner.toString}]"
  }

  class SeqTypeInformation[T](inner: TypeInformation[T])
      extends IterableTypeInformation[T, Seq](inner) {

    override def toString(): String = s"Seq[${inner.toString}]"
  }

  class SetTypeInformation[T](inner: TypeInformation[T])
      extends IterableTypeInformation[T, Set](inner) {

    override def toString(): String = s"Set[${inner.toString}]"
  }

  class VectorTypeInformation[T](inner: TypeInformation[T])
      extends IterableTypeInformation[T, Vector](inner) {

    override def toString(): String = s"Vector[${inner.toString}]"
  }

}
