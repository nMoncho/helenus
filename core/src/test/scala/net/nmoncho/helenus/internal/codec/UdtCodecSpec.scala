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

package net.nmoncho.helenus
package internal.codec

import java.util.UUID

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.api.DefaultColumnNamingScheme
import net.nmoncho.helenus.api.SnakeCase
import net.nmoncho.helenus.internal.codec.UdtCodecSpec.IceCream3
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UdtCodecSpec extends AnyWordSpec with Matchers {
  import UdtCodecSpec._

  private val codec: TypeCodec[IceCream] = Codec.udtOf[IceCream]()

  "UdtCodec" should {

    "round-trip (encode-decode) properly" in {
      val sundae  = IceCream("Sundae", 3, cone = false)
      val vanilla = IceCream("Vanilla", 3, cone = true)

      val round =
        codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

      round shouldBe sundae
      round should not be vanilla
    }

    "encode-decode a case class with a tuple" in {
      val codec: TypeCodec[IceCream2] = Codec.udtOf[IceCream2]()

      val sundae  = IceCream2("Sundae", 3, cone = false, 1 -> 2)
      val vanilla = IceCream2("Vanilla", 3, cone = true, 2 -> 1)

      val round =
        codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

      round shouldBe sundae
      round should not be vanilla
    }

    "encode-decode a case class with a options" in {
      val codec: TypeCodec[IceCream3] = Codec.udtOf[IceCream3]()

      withClue("with defined values") {
        val sundae  = IceCream3("Sundae", 3, cone = Some(false), Some(1 -> 2))
        val vanilla = IceCream3("Vanilla", 3, cone = Some(true), Some(2 -> 1))

        val round =
          codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

        round shouldBe sundae
        round should not be vanilla
      }

      withClue("with not defined values") {
        val sundae  = IceCream3("Sundae", 3, cone = None, None)
        val vanilla = IceCream3("Vanilla", 3, cone = Some(true), Some(2 -> 1))

        val round =
          codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

        round shouldBe sundae
        round should not be vanilla
      }
    }

    "create a codec from fields" in {
      implicit val colMapper: ColumnNamingScheme = DefaultColumnNamingScheme
      val codec: TypeCodec[IceCream3] =
        Codec.udtFromFields[IceCream3]("", "", true)(_.name, _.cone, _.count, _.numCherries)

      val sundae  = IceCream3("Sundae", 3, cone = Some(false), Some(1 -> 2))
      val vanilla = IceCream3("Vanilla", 3, cone = Some(true), Some(2 -> 1))

      val round =
        codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

      round shouldBe sundae
      round should not be vanilla
    }

    "format-parse" in {
      val vanilla = IceCream("Vanilla", 3, cone = true)

      codec.parse(codec.format(vanilla)) shouldEqual vanilla
    }

  }
}

object UdtCodecSpec {
  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  case class IceCream2(name: String, numCherries: Int, cone: Boolean, count: (Int, Int))

  case class IceCream3(
      name: String,
      numCherries: Int,
      cone: Option[Boolean],
      count: Option[(Int, Int)]
  ) {
    val shouldBeIgnored: String = "bar"
  }

}

class CassandraUdtCodecSpec extends AnyWordSpec with Matchers with CassandraSpec {

  override protected lazy val keyspace: String = "udt_codec_tests"

  "UdtCodec" should {
    "work with Cassandra" in {
      val id = UUID.randomUUID()
      query(id) shouldBe empty

      val ice = IceCream("Vanilla", 2, cone = false)
      insert(id, ice, IceCream.codec)

      val rowOpt = query(id)
      rowOpt shouldBe defined
      rowOpt.foreach(row => row.get("ice", IceCream.codec) shouldBe ice)
    }

    "work when fields are in different order (with session)" in {
      val id = UUID.randomUUID()
      query(id) shouldBe empty

      val ice = IceCreamShuffled(2, cone = false, "Vanilla")
      insert(id, ice, IceCreamShuffled.codec)

      val rowOpt = query(id)
      rowOpt shouldBe defined
      rowOpt.foreach(row => row.get("ice", IceCreamShuffled.codec) shouldBe ice)
    }

    "work when fields are in different order (with fields)" in {
      implicit val colMapper: ColumnNamingScheme = SnakeCase
      val codec: TypeCodec[IceCreamShuffled] =
        Codec.udtFromFields[IceCreamShuffled]("", "", true)(_.name, _.numCherries, _.cone)

      val id = UUID.randomUUID()
      query(id) shouldBe empty

      val ice = IceCreamShuffled(2, cone = false, "Vanilla")
      insert(id, ice, codec)

      val rowOpt = query(id)
      rowOpt shouldBe defined
      rowOpt.foreach(row => row.get("ice", codec) shouldBe ice)
    }

    "fail on invalid mapping" in {
      val ice = IceCreamInvalid(2, cone = false, "Vanilla")
      val exception = intercept[IllegalArgumentException](
        insert(UUID.randomUUID(), ice, IceCreamInvalid.codec)
      )

      exception.getMessage should include("cherries_number is not a field in this UDT")
    }

    "be registered on the session" in {
      val codec: TypeCodec[IceCream3] = Codec.udtOf[IceCream3]()

      session.registerCodecs(codec).isSuccess shouldBe true
    }
  }

  private def query(id: UUID): Option[Row] = {
    val rs = session.execute(s"SELECT * from udt_table WHERE id = $id")
    Option(rs.one())
  }

  private def insert[T](id: UUID, ice: T, codec: TypeCodec[T]): Unit = {
    val pstmt = session.prepare("INSERT INTO udt_table(id, ice) VALUES (?, ?)")
    val bstmt = pstmt
      .bind()
      .set(0, id, uuidCodec)
      .set(1, ice, codec)
    session.execute(bstmt)
  }

  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  object IceCream {
    implicit val codec: TypeCodec[IceCream] = Codec.udtOf[IceCream]()
  }

  case class IceCreamShuffled(numCherries: Int, cone: Boolean, name: String)

  object IceCreamShuffled {
    implicit val colMapper: ColumnNamingScheme = SnakeCase

    implicit val codec: TypeCodec[IceCreamShuffled] =
      Codec.udtFrom[IceCreamShuffled](session, name = "ice_cream")
  }

  case class IceCreamInvalid(cherriesNumber: Int, cone: Boolean, name: String)

  object IceCreamInvalid {
    implicit val colMapper: ColumnNamingScheme = SnakeCase

    implicit val codec: TypeCodec[IceCreamInvalid] =
      Codec.udtFrom[IceCreamInvalid](session, name = "ice_cream")
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(
      "CREATE TYPE IF NOT EXISTS ice_cream (name TEXT, num_cherries INT, cone BOOLEAN)"
    )
    executeDDL("""CREATE TABLE IF NOT EXISTS udt_table(
        |   id      UUID,
        |   ice     ice_cream,
        |   PRIMARY KEY (id)
        |)""".stripMargin)

  }
}
