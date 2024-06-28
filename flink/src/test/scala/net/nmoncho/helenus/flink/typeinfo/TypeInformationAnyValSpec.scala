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
