/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo
package collection

import org.apache.flink.api.common.typeinfo.TypeInformation

// $COVERAGE-OFF$
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
// $COVERAGE-ON$
