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

import org.apache.flink.api.java.tuple

// $COVERAGE-OFF$
trait FlinkTupleAsScalaConversions {

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  //
  //  def template(typeParameterCount: Int): Unit = {
  //    val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //    val tupleParams = (1 to typeParameterCount).map(i => s"t.f${i - 1}").mkString(", ")
  //
  //    println(
  //      s"""|implicit class FlinkTuple${typeParameterCount}HasAsScala[$typeParams](t: tuple.Tuple$typeParameterCount[$typeParams]) {
  //          |  def asScala(): ($typeParams) = ($tupleParams)
  //          |}""".stripMargin
  //    )
  //  }
  //
  //  (1 to 22).foreach(template)

  // format: off

  implicit class FlinkTuple1HasAsScala[T1](t: tuple.Tuple1[T1]) {
    def asScala(): Tuple1[T1] = Tuple1(t.f0)
  }

  implicit class FlinkTuple2HasAsScala[T1, T2](t: tuple.Tuple2[T1, T2]) {
    def asScala(): (T1, T2) = (t.f0, t.f1)
  }

  implicit class FlinkTuple3HasAsScala[T1, T2, T3](t: tuple.Tuple3[T1, T2, T3]) {
    def asScala(): (T1, T2, T3) = (t.f0, t.f1, t.f2)
  }

  implicit class FlinkTuple4HasAsScala[T1, T2, T3, T4](t: tuple.Tuple4[T1, T2, T3, T4]) {
    def asScala(): (T1, T2, T3, T4) = (t.f0, t.f1, t.f2, t.f3)
  }

  implicit class FlinkTuple5HasAsScala[T1, T2, T3, T4, T5](t: tuple.Tuple5[T1, T2, T3, T4, T5]) {
    def asScala(): (T1, T2, T3, T4, T5) = (t.f0, t.f1, t.f2, t.f3, t.f4)
  }

  implicit class FlinkTuple6HasAsScala[T1, T2, T3, T4, T5, T6](t: tuple.Tuple6[T1, T2, T3, T4, T5, T6]) {
    def asScala(): (T1, T2, T3, T4, T5, T6) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5)
  }

  implicit class FlinkTuple7HasAsScala[T1, T2, T3, T4, T5, T6, T7](t: tuple.Tuple7[T1, T2, T3, T4, T5, T6, T7]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6)
  }

  implicit class FlinkTuple8HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8](t: tuple.Tuple8[T1, T2, T3, T4, T5, T6, T7, T8]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7)
  }

  implicit class FlinkTuple9HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9](t: tuple.Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8)
  }

  implicit class FlinkTuple10HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t: tuple.Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9)
  }

  implicit class FlinkTuple11HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](t: tuple.Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10)
  }

  implicit class FlinkTuple12HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](t: tuple.Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11)
  }

  implicit class FlinkTuple13HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](t: tuple.Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12)
  }

  implicit class FlinkTuple14HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](t: tuple.Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13)
  }

  implicit class FlinkTuple15HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](t: tuple.Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14)
  }

  implicit class FlinkTuple16HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](t: tuple.Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15)
  }

  implicit class FlinkTuple17HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](t: tuple.Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16)
  }

  implicit class FlinkTuple18HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](t: tuple.Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17)
  }

  implicit class FlinkTuple19HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](t: tuple.Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18)
  }

  implicit class FlinkTuple20HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](t: tuple.Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19)
  }

  implicit class FlinkTuple21HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](t: tuple.Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19, t.f20)
  }

  implicit class FlinkTuple22HasAsScala[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](t: tuple.Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22]) {
    def asScala(): (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22) = (t.f0, t.f1, t.f2, t.f3, t.f4, t.f5, t.f6, t.f7, t.f8, t.f9, t.f10, t.f11, t.f12, t.f13, t.f14, t.f15, t.f16, t.f17, t.f18, t.f19, t.f20, t.f21)
  }

  // format: on
}

trait ScalaTupleAsFlinkConversions {

  // **********************************************************************
  // To generate methods to Tuple2 and above, use this template method.
  // **********************************************************************
  //
  //  def template(typeParameterCount: Int): Unit = {
  //    val typeParams = (1 to typeParameterCount).map(i => s"T$i").mkString(", ")
  //    val tupleParams = (1 to typeParameterCount).map(i => s"t._$i").mkString(", ")
  //
  //    println(
  //      s"""|implicit class ScalaTuple${typeParameterCount}HasAsFlink[$typeParams](t: ($typeParams)) {
  //          |  def asFlink(): tuple.Tuple$typeParameterCount[$typeParams] = tuple.Tuple$typeParameterCount.of($tupleParams)
  //          |}\n""".stripMargin
  //    )
  //  }
  //
  //  (1 to 22).foreach(template)

  // format: off

  implicit class ScalaTuple1HasAsFlink[T1](t: Tuple1[T1]) {
    def asFlink(): tuple.Tuple1[T1] = tuple.Tuple1.of(t._1)
  }

  implicit class ScalaTuple2HasAsFlink[T1, T2](t: (T1, T2)) {
    def asFlink(): tuple.Tuple2[T1, T2] = tuple.Tuple2.of(t._1, t._2)
  }

  implicit class ScalaTuple3HasAsFlink[T1, T2, T3](t: (T1, T2, T3)) {
    def asFlink(): tuple.Tuple3[T1, T2, T3] = tuple.Tuple3.of(t._1, t._2, t._3)
  }

  implicit class ScalaTuple4HasAsFlink[T1, T2, T3, T4](t: (T1, T2, T3, T4)) {
    def asFlink(): tuple.Tuple4[T1, T2, T3, T4] = tuple.Tuple4.of(t._1, t._2, t._3, t._4)
  }

  implicit class ScalaTuple5HasAsFlink[T1, T2, T3, T4, T5](t: (T1, T2, T3, T4, T5)) {
    def asFlink(): tuple.Tuple5[T1, T2, T3, T4, T5] = tuple.Tuple5.of(t._1, t._2, t._3, t._4, t._5)
  }

  implicit class ScalaTuple6HasAsFlink[T1, T2, T3, T4, T5, T6](t: (T1, T2, T3, T4, T5, T6)) {
    def asFlink(): tuple.Tuple6[T1, T2, T3, T4, T5, T6] = tuple.Tuple6.of(t._1, t._2, t._3, t._4, t._5, t._6)
  }

  implicit class ScalaTuple7HasAsFlink[T1, T2, T3, T4, T5, T6, T7](t: (T1, T2, T3, T4, T5, T6, T7)) {
    def asFlink(): tuple.Tuple7[T1, T2, T3, T4, T5, T6, T7] = tuple.Tuple7.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7)
  }

  implicit class ScalaTuple8HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8](t: (T1, T2, T3, T4, T5, T6, T7, T8)) {
    def asFlink(): tuple.Tuple8[T1, T2, T3, T4, T5, T6, T7, T8] = tuple.Tuple8.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)
  }

  implicit class ScalaTuple9HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9)) {
    def asFlink(): tuple.Tuple9[T1, T2, T3, T4, T5, T6, T7, T8, T9] = tuple.Tuple9.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)
  }

  implicit class ScalaTuple10HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)) {
    def asFlink(): tuple.Tuple10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10] = tuple.Tuple10.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)
  }

  implicit class ScalaTuple11HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)) {
    def asFlink(): tuple.Tuple11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11] = tuple.Tuple11.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)
  }

  implicit class ScalaTuple12HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12)) {
    def asFlink(): tuple.Tuple12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12] = tuple.Tuple12.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)
  }

  implicit class ScalaTuple13HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13)) {
    def asFlink(): tuple.Tuple13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13] = tuple.Tuple13.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)
  }

  implicit class ScalaTuple14HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14)) {
    def asFlink(): tuple.Tuple14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14] = tuple.Tuple14.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)
  }

  implicit class ScalaTuple15HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15)) {
    def asFlink(): tuple.Tuple15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15] = tuple.Tuple15.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)
  }

  implicit class ScalaTuple16HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16)) {
    def asFlink(): tuple.Tuple16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16] = tuple.Tuple16.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)
  }

  implicit class ScalaTuple17HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17)) {
    def asFlink(): tuple.Tuple17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17] = tuple.Tuple17.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)
  }

  implicit class ScalaTuple18HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18)) {
    def asFlink(): tuple.Tuple18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18] = tuple.Tuple18.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)
  }

  implicit class ScalaTuple19HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19)) {
    def asFlink(): tuple.Tuple19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19] = tuple.Tuple19.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)
  }

  implicit class ScalaTuple20HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20)) {
    def asFlink(): tuple.Tuple20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20] = tuple.Tuple20.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20)
  }

  implicit class ScalaTuple21HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21)) {
    def asFlink(): tuple.Tuple21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21] = tuple.Tuple21.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21)
  }

  implicit class ScalaTuple22HasAsFlink[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](t: (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22)) {
    def asFlink(): tuple.Tuple22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22] = tuple.Tuple22.of(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19, t._20, t._21, t._22)
  }

  // format: on

}

trait TupleConversions extends FlinkTupleAsScalaConversions with ScalaTupleAsFlinkConversions

object TupleConversions extends TupleConversions
// $COVERAGE-ON$
