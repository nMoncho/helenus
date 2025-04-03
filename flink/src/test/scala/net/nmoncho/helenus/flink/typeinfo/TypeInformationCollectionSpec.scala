/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import scala.reflect.ClassTag

import net.nmoncho.helenus.flink.models.Hotel
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.DataInputDeserializer
import org.apache.flink.core.memory.DataOutputSerializer
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class TypeInformationCollectionSpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with TypeGenerators
    with ImplicitTypes
    with TypeInformationDerivation {

  private val successful = minSuccessful(500)
  private val config     = new ExecutionConfig

  verifyTypeInfoRoundTripForScalaJavaCollection[Int, List, java.util.List]()

  verifyTypeInfoRoundTripForScalaJavaCollection[Hotel, List, java.util.List]()

  // format: off
  private def verifyTypeInfoRoundTripForScalaJavaCollection[A, S[_] <: Iterable[_], J[_] <: java.util.Collection[_]](
      successCount: MinSuccessful = successful
  )(
      implicit typeInfoA: TypeInformation[A],
      tagA: ClassTag[A],
      typeInfoScalaColl: TypeInformation[S[A]],
      tagScalaColl: ClassTag[S[_]],
      typeInfoJavaColl: TypeInformation[J[A]],
      tagJavaColl: ClassTag[J[_]],
      genScalaColl: GenN[S, A],
      genJavaColl: GenN[J, A]
  ): Unit =
    property(
      s"TypeInformation ${tagScalaColl.runtimeClass.getSimpleName}[${tagA.runtimeClass.getSimpleName}]<->java.util.${tagJavaColl.runtimeClass.getSimpleName}[${tagA.runtimeClass.getSimpleName}] should work round-trip"
    ) {
      val serializerInner          = typeInfoA.createSerializer(config)
      val collectionLengthOverhead = 50
      val elementLength = if (serializerInner.getLength == -1) 10 else serializerInner.getLength

      val serializerScalaColl = typeInfoScalaColl.createSerializer(config)
      val serializerJavaColl  = typeInfoJavaColl.createSerializer(config)

      forAll(genScalaColl(100), successCount) { valueA =>
        val viewA = new DataOutputSerializer(elementLength * valueA.size + collectionLengthOverhead)
        serializerScalaColl.serialize(valueA, viewA)

        val valueB =
          serializerJavaColl.deserialize(new DataInputDeserializer(viewA.getSharedBuffer))
        val viewB =
          new DataOutputSerializer(elementLength * valueB.size() + collectionLengthOverhead)
        serializerJavaColl.serialize(valueB, viewB)

        serializerScalaColl.deserialize(
          new DataInputDeserializer(viewB.getSharedBuffer)
        ) shouldBe valueA
      }

      forAll(genJavaColl(100), successCount) { valueB =>
        val viewB =
          new DataOutputSerializer(elementLength * valueB.size() + collectionLengthOverhead)
        serializerJavaColl.serialize(valueB, viewB)

        val valueA =
          serializerScalaColl.deserialize(new DataInputDeserializer(viewB.getSharedBuffer))
        val viewA = new DataOutputSerializer(elementLength * valueA.size + collectionLengthOverhead)
        serializerScalaColl.serialize(valueA, viewA)

        serializerJavaColl.deserialize(
          new DataInputDeserializer(viewA.getSharedBuffer)
        ) shouldBe valueB
      }
    }
  // format: on
}
