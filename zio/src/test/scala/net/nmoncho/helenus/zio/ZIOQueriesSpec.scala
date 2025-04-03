/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.zio

import scala.util.Try

import _root_.zio._
import _root_.zio.stream._
import _root_.zio.test.Assertion._
import _root_.zio.test._
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.RowMapper
import net.nmoncho.helenus.api.cql.Adapter

object ZIOQueriesSpec extends ZCassandraSpec {

  override def spec = suite("Helenus")(
    syncSimpleInsertQueryTest,
    syncQueryWithParametersTest,
    syncInterpolatedInsertQueryTest,
    asyncSimpleInsertQueryTest,
    asyncQueryWithParametersTest,
    asyncInterpolatedInsertQueryTest,
    streamsInsertQueryTest,
    errorHandlingTest,
    errorAsyncHandlingTest,
    errorStreamHandlingTest,
    syncPaginationNextOptionTest,
    asyncPaginationTest,
    asyncPaginationNextOptionTest
  ) @@ TestAspect.beforeAll(beforeAll()) @@ TestAspect.before(beforeEach()) @@ TestAspect.sequential

  private def syncSimpleInsertQueryTest = test("should insert and query (sync) using ZIO") {
    import SyncQueries._

    for {
      queryBeforeInsert <- queryAllToList
      _ <- ZIO.foreach(ijes)(insertFrom)
      queryAfterInsert <- queryAllToList
    } yield {
      assertTrue(queryBeforeInsert.isEmpty) &&
      assertTrue(queryAfterInsert.nonEmpty) &&
      assertTrue(queryAfterInsert.toSet == ijes.toSet)
    }
  }

  private def syncQueryWithParametersTest = test("should query with parameters (sync) using ZIO") {
    import SyncQueries._

    // Insert all values
    assertZIO(ZIO.foreach(ijes)(insertFrom))(
      anything
    ) &&
    // check we get some for something that exists
    assertZIO(queryByName("vanilla"))(
      isSome(equalTo(vanilla))
    ) &&
    // but we get none for something that doesn't
    assertZIO(queryByName("crema del cielo"))(
      isNone
    ) &&
    // same as before, but for two parameters
    assertZIO(queryByNameAndCone("vanilla", cone = true))(
      isSome(equalTo(vanilla))
    ) &&
    assertZIO(queryByNameAndCone("chocolate", cone = true))(
      isNone
    )
  }

  private def syncInterpolatedInsertQueryTest =
    test("should insert and query (sync interpolated) using ZIO") {
      import SyncQueries._

      // Insert all values
      assertZIO(ZIO.foreach(ijes)(interpolatedInsertFrom))(
        anything
      ) &&
      // check we can get all the values again
      assertZIO(interpolatedQueryAllToList())(
        hasSameElements(ijes)
      ) &&
      // check we get some for something that exists
      assertZIO(interpolatedQueryByName("vanilla"))(
        isSome(equalTo(vanilla))
      ) &&
      // but we get none for something that doesn't
      assertZIO(interpolatedQueryByName("crema del cielo"))(
        isNone
      )
    }

  private def asyncSimpleInsertQueryTest = test("should insert and query (async) using ZIO") {
    import AsyncQueries._

    for {
      queryBeforeInsert <- queryAllToList
      _ <- ZIO.foreach(ijes)(insertFrom)
      queryAfterInsert <- queryAllToList
    } yield {
      assertTrue(queryBeforeInsert.isEmpty) &&
      assertTrue(queryAfterInsert.nonEmpty) &&
      assertTrue(queryAfterInsert.toSet == ijes.toSet)
    }
  }

  private def asyncQueryWithParametersTest =
    test("should query with parameters (async) using ZIO") {
      import AsyncQueries._

      // Insert all values
      assertZIO(ZIO.foreach(ijes)(insertFrom))(
        anything
      ) &&
      // check we get some for something that exists
      assertZIO(queryByName("vanilla"))(
        isSome(equalTo(vanilla))
      ) &&
      // but we get none for something that doesn't
      assertZIO(queryByName("crema del cielo"))(
        isNone
      ) &&
      // same as before, but for two parameters
      assertZIO(queryByNameAndCone("vanilla", cone = true))(
        isSome(equalTo(vanilla))
      ) &&
      assertZIO(queryByNameAndCone("chocolate", cone = true))(
        isNone
      )
    }

  private def asyncInterpolatedInsertQueryTest =
    test("should insert and query (async interpolated) using ZIO") {
      import AsyncQueries._

      // Insert all values
      assertZIO(ZIO.foreach(ijes)(interpolatedInsertFrom))(
        anything
      ) &&
      // check we can get all the values again
      assertZIO(interpolatedQueryAllToList())(
        hasSameElements(ijes)
      ) &&
      // check we get some for something that exists
      assertZIO(interpolatedQueryByName("vanilla"))(
        isSome(equalTo(vanilla))
      ) &&
      // but we get none for something that doesn't
      assertZIO(interpolatedQueryByName("crema del cielo"))(
        isNone
      )
    }

  private def streamsInsertQueryTest =
    test("should work with ZIO (streams) inserting and querying") {
      def queryAll() =
        "SELECT * FROM ice_creams".toZCQL.prepareUnitAsync
          .to[IceCream]
          .withOptions(_.withPageSize(2))
          .streamValidated()

      def iceCreamSink =
        "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
          .prepare[String, Int, Boolean]
          .from[IceCream]
          .sink()

      for {
        _ <- ZStream.fromIterable(ijes).run(iceCreamSink)
        query <- queryAll().run(ZSink.collectAll)
        all = query.foldLeft(List.empty[IceCream])(_ ++ _.toList)
      } yield assertTrue(all.nonEmpty)
    }

  private def errorHandlingTest = test("should handle (sync) errors correctly") {
    def insert(ice: IceCream) =
      "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .execute(ice)

    def queryHeadError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCreamError].execute().oneOption

    def queryAllError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCreamError].execute().to(List)

    def queryIterError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit
        .to[IceCreamError]
        .execute()
        .iterator
        .map(_.next())

    assertZIO(ZIO.foreach(ijes)(insert))(
      anything
    ) &&
    assertZIO(queryHeadError().exit)(
      fails(isSubtype[InvalidMappingException](anything))
    ) &&
    assertZIO(queryAllError().exit)(
      fails(isSubtype[InvalidMappingException](anything))
    ) &&
    assertZIO(queryIterError())(
      isFailure
    ) &&
    assertZIO("SELECT * ice_creams".toZCQL.prepareUnit.exit)(
      fails(isSubtype[InvalidStatementException](anything))
    )
  }

  private def errorAsyncHandlingTest = test("should handle (async) errors correctly") {
    def insert(ice: IceCream) =
      "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .execute(ice)

    def queryOneError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCreamError].executeAsync().oneOption

    def queryAllError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCreamError].executeAsync().to(List)

    def queryCurrentPageError() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit
        .to[IceCreamError]
        .executeAsync()
        .currentAndNextPage
        .map(_._1.next())

    assertZIO(ZIO.foreach(ijes)(insert))(
      anything
    ) &&
    assertZIO(queryOneError().exit)(
      fails(isSubtype[InvalidMappingException](anything))
    ) &&
    assertZIO(queryAllError().exit)(
      fails(isSubtype[InvalidMappingException](anything))
    ) &&
    assertZIO(ZIO.foreach(ijes)(insert) *> queryCurrentPageError())(
      isFailure
    ) &&
    assertZIO("SELECT * ice_creams".toZCQL.prepareUnitAsync.exit)(
      fails(isSubtype[InvalidStatementException](anything))
    )
  }

  private def errorStreamHandlingTest = test("should handle (stream) errors correctly") {
    def queryAll() =
      "SELECT * FROM ice_creams".toZCQL.prepareUnitAsync.to[IceCreamError].streamValidated()

    def iceCreamSink =
      "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .sink()

    assertZIO(
      ZStream.fromIterable(ijes).run(iceCreamSink) *> queryAll().run(ZSink.collectAll).exit
    )(
      fails(isSubtype[InvalidMappingException](anything))
    )
  }

  private def asyncPaginationTest = test("should paginate results with async execution") {
    val query = "SELECT * FROM ice_creams".toZCQL.prepareUnit
      .withOptions(_.withPageSize(1)) // there are only 3 values, so 3 pages (0), (1), (2)
      .to[IceCream]
      .executeAsync()

    for {
      _ <- ZIO.foreach(ijes)(SyncQueries.insertFrom)
      firstAndSecondPage <- query.currentAndNextPage
      (page1, zPage2) = firstAndSecondPage
      secondAndThirdPage <- zPage2.currentAndNextPage
      (page2, zPage3) = secondAndThirdPage
      thirdAndForthPage <- zPage3.currentAndNextPage
      (page3, zPage4) = thirdAndForthPage
      forthPage <- zPage4.currentAndNextPage
      (page4, _) = forthPage
    } yield {
      assertTrue(page1.nonEmpty) &&
      assertTrue(page2.nonEmpty) &&
      assertTrue(page3.nonEmpty) &&
      assertTrue(page4.isEmpty)
    }
  }

  private def syncPaginationNextOptionTest =
    test("should paginate results with sync execution (nextOption)") {
      val query = "SELECT * FROM ice_creams".toZCQL.prepareUnit
        .to[IceCream]
        .execute()

      for {
        _ <- ZIO.foreach(ijes)(SyncQueries.insertFrom)
        firstAndPage <- query.nextOption
        (first, zPage2) = firstAndPage
        secondAndPage <- zPage2.nextOption
        (second, zPage3) = secondAndPage
        thirdAndPage <- zPage3.nextOption
        (third, zPage4) = thirdAndPage
        forthAndPagePage <- zPage4.nextOption
        (forth, _) = forthAndPagePage

        // make sure that `to` fetches all records
        all <- query.to(List)
      } yield {
        assertTrue(first.nonEmpty) &&
        assertTrue(second.nonEmpty) &&
        assertTrue(third.nonEmpty) &&
        assertTrue(forth.isEmpty) &&
        assertTrue(all.toSet == ijes.toSet)
      }
    }

  private def asyncPaginationNextOptionTest =
    test("should paginate results with async execution (nextOption)") {
      val query = "SELECT * FROM ice_creams".toZCQL.prepareUnit
        .withOptions(_.withPageSize(2)) // there are only 3 values, so 2 pages (0, 1), (2)
        .to[IceCream]
        .executeAsync()

      for {
        _ <- ZIO.foreach(ijes)(SyncQueries.insertFrom)
        firstAndPage <- query.nextOption
        (first, zPage2) = firstAndPage
        secondAndPage <- zPage2.nextOption
        (second, zPage3) = secondAndPage
        thirdAndPage <- zPage3.nextOption
        (third, zPage4) = thirdAndPage
        forthAndPagePage <- zPage4.nextOption
        (forth, _) = forthAndPagePage

        // make sure that `to` fetches all records
        all <- query.to(List)
      } yield {
        assertTrue(first.nonEmpty) &&
        assertTrue(second.nonEmpty) &&
        assertTrue(third.nonEmpty) &&
        assertTrue(forth.isEmpty) &&
        assertTrue(all.toSet == ijes.toSet)
      }
    }

  private def beforeAll() = for {
    _ <- createKeyspace()
    _ <- executeDDL("""CREATE TABLE IF NOT EXISTS ice_creams(
                     |  name         TEXT PRIMARY KEY,
                     |  numCherries  INT,
                     |  cone         BOOLEAN
                     |)""".stripMargin)
  } yield ()

  private def beforeEach() = executeDDL("TRUNCATE TABLE ice_creams")

  object SyncQueries {

    final val TableName = "ice_creams"

    val queryAllToList: ZIO[ZCqlSession, Throwable, List[IceCream]] =
      "SELECT * FROM ice_creams".toZCQL.prepareUnit.to[IceCream].execute().to(List)

    def insertFrom(value: IceCream): ZPagingIterable[Try[Row]] =
      "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
        .prepare[String, Int, Boolean]
        .from[IceCream]
        .execute(value)

    def queryByName(name: String): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      "SELECT * FROM ice_creams WHERE name = ?".toZCQL
        .prepare[String]
        .to[IceCream]
        .execute(name)
        .oneOption

    def queryByNameAndCone(
        name: String,
        cone: Boolean
    ): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toZCQL
        .prepare[String, Boolean]
        .to[IceCream]
        .execute(name, cone)
        .oneOption

    def interpolatedQueryAllToList(): ZIO[ZCqlSession, Throwable, List[IceCream]] =
      zcql"SELECT * FROM $TableName".to[IceCream].execute().to(List)

    def interpolatedInsertFrom(value: IceCream): ZPagingIterable[Try[Row]] =
      zcql"INSERT INTO $TableName(name, numCherries, cone) VALUES (${value.name}, ${value.numCherries}, ${value.cone})"
        .execute()

    def interpolatedQueryByName(
        name: String
    ): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      zcql"SELECT * FROM $TableName WHERE name = $name".to[IceCream].execute().oneOption
  }

  object AsyncQueries {
    final val TableName = "ice_creams"

    val queryAllToList: ZIO[ZCqlSession, Throwable, List[IceCream]] =
      "SELECT * FROM ice_creams".toZCQL.prepareUnitAsync
        .to[IceCream]
        .executeAsync()
        .to(List)

    def insertFrom(value: IceCream): ZAsyncPagingIterable[Try[Row]] =
      "INSERT INTO ice_creams(name, numCherries, cone) VALUES(?, ?, ?)".toZCQL
        .prepareAsync[String, Int, Boolean]
        .from[IceCream]
        .executeAsync(value)

    def queryByName(name: String): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      "SELECT * FROM ice_creams WHERE name = ?".toZCQL
        .prepareAsync[String]
        .to[IceCream]
        .executeAsync(name)
        .oneOption

    def queryByNameAndCone(
        name: String,
        cone: Boolean
    ): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      "SELECT * FROM ice_creams WHERE name = ? AND cone = ? ALLOW FILTERING".toZCQL
        .prepareAsync[String, Boolean]
        .to[IceCream]
        .executeAsync(name, cone)
        .oneOption

    def interpolatedQueryAllToList(): ZIO[ZCqlSession, Throwable, List[IceCream]] =
      zcqlAsync"SELECT * FROM $TableName".to[IceCream].executeAsync().to(List)

    def interpolatedInsertFrom(value: IceCream): ZAsyncPagingIterable[Try[Row]] =
      zcqlAsync"INSERT INTO $TableName(name, numCherries, cone) VALUES (${value.name}, ${value.numCherries}, ${value.cone})"
        .executeAsync()

    def interpolatedQueryByName(
        name: String
    ): ZIO[ZCqlSession, Throwable, Option[IceCream]] =
      zcqlAsync"SELECT * FROM $TableName WHERE name = $name".to[IceCream].executeAsync().oneOption
  }

  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  object IceCream {
    implicit val rowMapper: RowMapper[IceCream] = RowMapper[IceCream]
    implicit val rowAdapter: Adapter[IceCream, (String, Int, Boolean)] =
      Adapter.builder[IceCream].build
  }

  case class IceCreamError(name: String, numOfCherries: Int, cone: Boolean)

  object IceCreamError {
    implicit val rowMapper: RowMapper[IceCreamError] = RowMapper[IceCreamError]
  }

  private val vanilla   = IceCream("vanilla", numCherries = 2, cone = true)
  private val chocolate = IceCream("chocolate", numCherries = 0, cone = false)
  private val theAnswer = IceCream("the answer", numCherries = 42, cone = true)

  private val ijes = List(
    vanilla,
    chocolate,
    theAnswer
  )
}
