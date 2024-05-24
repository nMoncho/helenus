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

package net.nmoncho.helenus.flink

import scala.language.implicitConversions

import org.apache.flink.api.java.tuple

// $COVERAGE-OFF$
trait ScalaTupleToFlinkImplicitConversions {

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  //
  //  def template(typeParameterCount: Int): Unit = {
  //    val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //    val tupleParams = (1 to typeParameterCount).map(i => s"t._$i").mkString(", ")
  //
  //    println(s"""implicit def scalaTupleAsFlinkIC[$typeParams](t: ($typeParams)): tuple.Tuple$typeParameterCount[$typeParams] = tuple.Tuple$typeParameterCount.of($tupleParams)\n""")
  //  }
  //
  //  (1 to 22).foreach(template)

  // format: off

  implicit def scalaTupleAsFlinkIC[T1](t: Tuple1[T1]): tuple.Tuple1[T1] = tuple.Tuple1.of(t._1)

  implicit def scalaTupleAsFlinkIC[T1, T2](t: (T1, T2)): tuple.Tuple2[T1, T2] = tuple.Tuple2.of(t._1, t._2)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3](t: (T1, T2, T3)): tuple.Tuple3[T1, T2, T3] = tuple.Tuple3.of(t._1, t._2, t._3)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4](t: (T1, T2, T3, T4)): tuple.Tuple4[T1, T2, T3, T4] = tuple.Tuple4.of(t._1, t._2, t._3, t._4)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5](t: (T1, T2, T3, T4, T5)): tuple.Tuple5[T1, T2, T3, T4, T5] = tuple.Tuple5.of(t._1, t._2, t._3, t._4, t._5)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6](t: (T1, T2, T3, T4, T5, T6)): tuple.Tuple6[T1, T2, T3, T4, T5, T6] = tuple.Tuple6.of(t._1, t._2, t._3, t._4, t._5, t._6)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7](t: (T1, T2, T3, T4, T5, T6, T7)): tuple.Tuple7[T1, T2, T3, T4, T5, T6, T7] = tuple.Tuple7.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8](t: (T1, T2, T3, T4, T5, T6, T7, T8)): tuple.Tuple8[T1, T2, T3, T4, T5, T6, T7, T8] = tuple.Tuple8.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9)): tuple.Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9] = tuple.Tuple9.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)): tuple.Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10] = tuple.Tuple10.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)): tuple.Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11] = tuple.Tuple11.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)): tuple.Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12] = tuple.Tuple12.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)): tuple.Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13] = tuple.Tuple13.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)): tuple.Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14] = tuple.Tuple14.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)): tuple.Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15] = tuple.Tuple15.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)): tuple.Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16] = tuple.Tuple16.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)): tuple.Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17] = tuple.Tuple17.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)): tuple.Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18] = tuple.Tuple18.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)): tuple.Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19] = tuple.Tuple19.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)): tuple.Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20] = tuple.Tuple20.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)): tuple.Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21] = tuple.Tuple21.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21)

  implicit def scalaTupleAsFlinkIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)): tuple.Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22] = tuple.Tuple22.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21, t._22)

  // format: on
}

trait FlinkTupleAsScalaImplicitConversions {

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  //
  //  def template(typeParameterCount: Int): Unit = {
  //    val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //    val tupleParams = (1 to typeParameterCount).map(i => s"t.f${i - 1}").mkString(", ")
  //
  //    println(s"""implicit def flinkTupleAsScalaIC[$typeParams](t: tuple.Tuple$typeParameterCount[$typeParams]): ($typeParams) = ($tupleParams)\n""")
  //  }

  // format: off

  implicit def flinkTupleAsScalaIC[T1](t: tuple.Tuple1[T1]): Tuple1[T1] = Tuple1(t.f0)

  implicit def flinkTupleAsScalaIC[T1, T2](t: tuple.Tuple2[T1, T2]): (T1, T2) = (t.f0, t.f1)

  implicit def flinkTupleAsScalaIC[T1, T2, T3](t: tuple.Tuple3[T1, T2, T3]): (T1, T2, T3) = (t.f0, t.f1, t.f2)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4](t: tuple.Tuple4[T1, T2, T3, T4]): (T1, T2, T3, T4) = (t.f0, t.f1, t.f2, t.f3)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5](t: tuple.Tuple5[T1, T2, T3, T4, T5]): (T1, T2, T3, T4, T5) = (t.f0, t.f1, t.f2, t.f3, t.f4)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6](t: tuple.Tuple6[T1, T2, T3, T4, T5, T6]): (T1, T2, T3, T4, T5, T6) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7](t: tuple.Tuple7[T1, T2, T3, T4, T5, T6, T7]): (T1, T2, T3, T4, T5, T6, T7) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8](t: tuple.Tuple8[T1, T2, T3, T4, T5, T6, T7, T8]): (T1, T2, T3, T4, T5, T6, T7, T8) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9](t: tuple.Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9]): (T1, T2, T3, T4, T5, T6, T7, T8, T9) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t: tuple.Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](t: tuple.Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](t: tuple.Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](t: tuple.Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](t: tuple.Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](t: tuple.Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](t: tuple.Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](t: tuple.Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](t: tuple.Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](t: tuple.Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](t: tuple.Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](t: tuple.Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19, t.f20)

  implicit def flinkTupleAsScalaIC[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](t: tuple.Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22]): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19, t.f20, t.f21)

  // format: on

}

trait TupleImplicitConversions
    extends ScalaTupleToFlinkImplicitConversions
    with FlinkTupleAsScalaImplicitConversions

object TupleImplicitConversions extends TupleImplicitConversions
// $COVERAGE-ON$
