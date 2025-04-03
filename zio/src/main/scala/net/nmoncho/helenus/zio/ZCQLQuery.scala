/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.StatementOptions
import net.nmoncho.helenus.internal.cql._
import zio._

class ZCQLQuery(val query: String) extends ZCQLQuery.SyncCQLQuery with ZCQLQuery.AsyncCQLQuery

object ZCQLQuery {

  /** Defines synchronous `prepare` query methods
    */
  trait SyncCQLQuery {

    def query: String

    /** Prepares a query that doesn't take any query parameters
      */
    def prepareUnit: ZScalaPreparedStatementUnit[Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatementUnit(_, RowMapper.identity, StatementOptions.default)
        )
      )

    /** Prepares a query that will take 1 query parameter
      */
    def prepare[T1](implicit t1: TypeCodec[T1]): ZScalaPreparedStatement1[T1, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement1(_, RowMapper.identity, StatementOptions.default, t1)
        )
      )

    // format: off
    /** Prepares a query that will take 2 query parameters
     */
    def prepare[T1, T2](implicit t1: TypeCodec[T1], t2: TypeCodec[T2]): ZScalaPreparedStatement2[T1, T2, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement2(_, RowMapper.identity, StatementOptions.default, t1, t2)
        )
      )

    /** Prepares a query that will take 3 query parameters
     */
    def prepare[T1, T2, T3](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3]): ZScalaPreparedStatement3[T1, T2, T3, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement3(_, RowMapper.identity, StatementOptions.default, t1, t2, t3)
        )
      )

    // $COVERAGE-OFF$
    /** Prepares a query that will take 4 query parameters
     */
    def prepare[T1, T2, T3, T4](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4]): ZScalaPreparedStatement4[T1, T2, T3, T4, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement4(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4)
        )
      )

    /** Prepares a query that will take 5 query parameters
     */
    def prepare[T1, T2, T3, T4, T5](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5]): ZScalaPreparedStatement5[T1, T2, T3, T4, T5, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement5(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5)
        )
      )

    /** Prepares a query that will take 6 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6]): ZScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement6(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6)
        )
      )

    /** Prepares a query that will take 7 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7]): ZScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement7(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7)
        )
      )

    /** Prepares a query that will take 8 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8]): ZScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement8(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8)
        )
      )

    /** Prepares a query that will take 9 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9]): ZScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement9(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9)
        )
      )

    /** Prepares a query that will take 10 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10]): ZScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement10(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)
        )
      )

    /** Prepares a query that will take 11 query parameter
      */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11]): ZScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement11(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11)
        )
      )

    // format: off
    /** Prepares a query that will take 12 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12]): ZScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement12(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12)
        )
      )

    /** Prepares a query that will take 13 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13]): ZScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement13(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13)
        )
      )

    /** Prepares a query that will take 14 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14]): ZScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement14(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14)
        )
      )

    /** Prepares a query that will take 15 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15]): ZScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement15(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15)
        )
      )

    /** Prepares a query that will take 16 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16]): ZScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement16(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16)
        )
      )

    /** Prepares a query that will take 17 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17]): ZScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement17(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17)
        )
      )

    /** Prepares a query that will take 18 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18]): ZScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement18(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18)
        )
      )

    /** Prepares a query that will take 19 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19]): ZScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement19(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19)
        )
      )

    /** Prepares a query that will take 20 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20]): ZScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement20(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20)
        )
      )
    
    /** Prepares a query that will take 21 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21]): ZScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement21(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21)
        )
      )
    
    /** Prepares a query that will take 21 query parameters
     */
    def prepare[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21], t22: TypeCodec[T22]): ZScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepare(query).map(
          new ScalaPreparedStatement22(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22)
        )
      )
    // format: on
    // $COVERAGE-ON$
  }

  /** Defines asynchronous `prepare` query methods
    */
  trait AsyncCQLQuery {

    def query: String

    /** Prepares a query that doesn't take any query parameters
      */
    def prepareUnitAsync: ZScalaPreparedStatementUnit[Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatementUnit(_, RowMapper.identity, StatementOptions.default)
        )
      )

    /** Prepares a query that will take 1 query parameter
      */
    def prepareAsync[T1](implicit t1: TypeCodec[T1]): ZScalaPreparedStatement1[T1, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement1(_, RowMapper.identity, StatementOptions.default, t1)
        )
      )

    // format: off
    /** Prepares a query that will take 2 query parameters
      */
    def prepareAsync[T1, T2](implicit t1: TypeCodec[T1], t2: TypeCodec[T2]): ZScalaPreparedStatement2[T1, T2, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement2(_, RowMapper.identity, StatementOptions.default, t1, t2)
        )
      )

    /** Prepares a query that will take 3 query parameters
      */
    def prepareAsync[T1, T2, T3](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3]): ZScalaPreparedStatement3[T1, T2, T3, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement3(_, RowMapper.identity, StatementOptions.default, t1, t2, t3)
        )
      )

    // $COVERAGE-OFF$
    /** Prepares a query that will take 4 query parameters
     */
    def prepareAsync[T1, T2, T3, T4](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4]): ZScalaPreparedStatement4[T1, T2, T3, T4, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement4(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4)
        )
      )

    /** Prepares a query that will take 5 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5]): ZScalaPreparedStatement5[T1, T2, T3, T4, T5, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement5(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5)
        )
      )

    /** Prepares a query that will take 6 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6]): ZScalaPreparedStatement6[T1, T2, T3, T4, T5, T6, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement6(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6)
        )
      )

    /** Prepares a query that will take 7 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7]): ZScalaPreparedStatement7[T1, T2, T3, T4, T5, T6, T7, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement7(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7)
        )
      )

    /** Prepares a query that will take 8 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8]): ZScalaPreparedStatement8[T1, T2, T3, T4, T5, T6, T7, T8, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement8(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8)
        )
      )

    /** Prepares a query that will take 9 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9]): ZScalaPreparedStatement9[T1, T2, T3, T4, T5, T6, T7, T8, T9, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement9(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9)
        )
      )

    /** Prepares a query that will take 10 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10]): ZScalaPreparedStatement10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement10(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)
        )
      )

    /** Prepares a query that will take 11 query parameter
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11]): ZScalaPreparedStatement11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement11(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11)
        )
      )

    // format: off
    /** Prepares a query that will take 12 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12]): ZScalaPreparedStatement12[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement12(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12)
        )
      )

    /** Prepares a query that will take 13 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13]): ZScalaPreparedStatement13[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement13(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13)
        )
      )

    /** Prepares a query that will take 14 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14]): ZScalaPreparedStatement14[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement14(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14)
        )
      )

    /** Prepares a query that will take 15 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15]): ZScalaPreparedStatement15[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement15(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15)
        )
      )

    /** Prepares a query that will take 16 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16]): ZScalaPreparedStatement16[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement16(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16)
        )
      )

    /** Prepares a query that will take 17 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17]): ZScalaPreparedStatement17[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement17(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17)
        )
      )

    /** Prepares a query that will take 18 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18]): ZScalaPreparedStatement18[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement18(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18)
        )
      )

    /** Prepares a query that will take 19 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19]): ZScalaPreparedStatement19[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement19(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19)
        )
      )

    /** Prepares a query that will take 20 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20]): ZScalaPreparedStatement20[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement20(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20)
        )
      )

    /** Prepares a query that will take 21 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21]): ZScalaPreparedStatement21[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement21(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21)
        )
      )

    /** Prepares a query that will take 21 query parameters
     */
    def prepareAsync[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22](implicit t1: TypeCodec[T1], t2: TypeCodec[T2], t3: TypeCodec[T3], t4: TypeCodec[T4], t5: TypeCodec[T5], t6: TypeCodec[T6], t7: TypeCodec[T7], t8: TypeCodec[T8], t9: TypeCodec[T9], t10: TypeCodec[T10], t11: TypeCodec[T11], t12: TypeCodec[T12], t13: TypeCodec[T13], t14: TypeCodec[T14], t15: TypeCodec[T15], t16: TypeCodec[T16], t17: TypeCodec[T17], t18: TypeCodec[T18], t19: TypeCodec[T19], t20: TypeCodec[T20], t21: TypeCodec[T21], t22: TypeCodec[T22]): ZScalaPreparedStatement22[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, T22, Row] =
      ZIO.serviceWithZIO[ZCqlSession](
        _.prepareAsync(query).map(
          new ScalaPreparedStatement22(_, RowMapper.identity, StatementOptions.default, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, t20, t21, t22)
        )
      )
    // format: on
    // $COVERAGE-ON$
  }
}
