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

import scala.jdk.CollectionConverters._

import net.nmoncho.helenus.flink.models.Address
import net.nmoncho.helenus.flink.models.Hotel
import org.scalacheck.Gen
import org.scalacheck.util.Buildable

trait TypeGenerators {

  type GenN[F[_], A] = Int => Gen[F[A]]

  protected implicit val genBoolean: Gen[Boolean] = Gen.oneOf(true, false)
  protected implicit val genJBoolean: Gen[java.lang.Boolean] =
    genBoolean.map(x => x: java.lang.Boolean)

  protected implicit val genByte: Gen[Byte]            = Gen.choose(Byte.MinValue, Byte.MaxValue)
  protected implicit val genJByte: Gen[java.lang.Byte] = genByte.map(x => x: java.lang.Byte)

  protected implicit val genChar: Gen[Char] = Gen.choose(Char.MinValue, Char.MaxValue)
  protected implicit val genCharacter: Gen[java.lang.Character] =
    genChar.map(x => x: java.lang.Character)

  protected implicit val genShort: Gen[Short] = Gen.choose(Short.MinValue, Short.MaxValue)
  protected implicit val genJShort: Gen[java.lang.Short] = genShort.map(x => x: java.lang.Short)

  protected implicit val genInt: Gen[Int]                   = Gen.choose(Int.MinValue, Int.MaxValue)
  protected implicit val genInteger: Gen[java.lang.Integer] = genInt.map(x => x: java.lang.Integer)

  protected implicit val genLong: Gen[Long]            = Gen.choose(Long.MinValue, Long.MaxValue)
  protected implicit val genJLong: Gen[java.lang.Long] = genLong.map(x => x: java.lang.Long)

  protected implicit val genFloat: Gen[Float] = Gen.choose(Float.MinValue, Float.MaxValue)
  protected implicit val genJFloat: Gen[java.lang.Float] = genFloat.map(x => x: java.lang.Float)

  protected implicit val genDouble: Gen[Double] = Gen.choose(Double.MinValue, Double.MaxValue)
  protected implicit val genJDouble: Gen[java.lang.Double] = genDouble.map(x => x: java.lang.Double)

  protected implicit val genAddress: Gen[Address] = for {
    street <- Gen.asciiPrintableStr
    city <- Gen.asciiPrintableStr
    stateOrProvince <- Gen.asciiPrintableStr
    postalCode <- Gen.asciiPrintableStr
    country <- Gen.asciiPrintableStr
  } yield Address(street, city, stateOrProvince, postalCode, country)

  protected implicit val genHotel: Gen[Hotel] = for {
    id <- Gen.asciiPrintableStr
    name <- Gen.asciiPrintableStr
    phone <- Gen.asciiPrintableStr
    address <- genAddress
  } yield Hotel(id, name, phone, address)

  protected implicit def genJList[A](implicit genList: GenN[List, A]): GenN[java.util.List, A] =
    n => genList(n).map(_.asJava)

  protected implicit def genContainer[F[_], A](
      implicit genA: Gen[A],
      evb: Buildable[A, F[A]],
      evt: F[A] => Traversable[A]
  ): GenN[F, A] = Gen.containerOfN[F, A](_, genA)
}
