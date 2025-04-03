/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.flink.typeinfo

import scala.reflect.ClassTag

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.DataInputDeserializer
import org.apache.flink.core.memory.DataOutputSerializer
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class TypeInformationAnyValSpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with TypeGenerators
    with ImplicitTypes
    with TypeInformationDerivation {

  private val successful = minSuccessful(500)
  private val config     = new ExecutionConfig

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Boolean, java.lang.Boolean]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Byte, java.lang.Byte]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Char, java.lang.Character]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Short, java.lang.Short]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Int, java.lang.Integer]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Long, java.lang.Long]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Float, java.lang.Float]()

  verifyTypeInfoRoundTripForScalaJavaAnyVal[Double, java.lang.Double]()

  private def verifyTypeInfoRoundTripForScalaJavaAnyVal[A, B](
      successCount: MinSuccessful = successful
  )(
      implicit typeInfoA: TypeInformation[A],
      tagA: ClassTag[A],
      genA: Gen[A],
      typeInfoB: TypeInformation[B],
      tagB: ClassTag[B],
      genB: Gen[B]
  ): Unit =
    property(
      s"TypeInformation ${tagA.runtimeClass.getSimpleName}<->${tagB.runtimeClass.getSimpleName} should work round-trip"
    ) {
      val serializerA = typeInfoA.createSerializer(config)
      val serializerB = typeInfoB.createSerializer(config)

      forAll(genA, successCount) { valueA =>
        val viewA = new DataOutputSerializer(serializerA.getLength)
        serializerA.serialize(valueA, viewA)

        val valueB = serializerB.deserialize(new DataInputDeserializer(viewA.getSharedBuffer))
        val viewB  = new DataOutputSerializer(serializerB.getLength)
        serializerB.serialize(valueB, viewB)

        serializerA.deserialize(new DataInputDeserializer(viewB.getSharedBuffer)) shouldBe valueA
      }

      forAll(genB, successCount) { valueB =>
        val viewB = new DataOutputSerializer(serializerB.getLength)
        serializerB.serialize(valueB, viewB)

        val valueA = serializerA.deserialize(new DataInputDeserializer(viewB.getSharedBuffer))
        val viewA  = new DataOutputSerializer(serializerA.getLength)
        serializerA.serialize(valueA, viewA)

        serializerB.deserialize(new DataInputDeserializer(viewA.getSharedBuffer)) shouldBe valueB
      }
    }
}
