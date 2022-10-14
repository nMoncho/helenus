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

import com.datastax.oss.driver.api.core.ProtocolVersion
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import net.nmoncho.helenus.api.`type`.codec.{ ColumnMapper, SnakeCase, TimeUuid, Udt }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class UdtCodecSpec extends AnyWordSpec with Matchers {
  import UdtCodecSpec._

  private val codec: TypeCodec[IceCream] = Codec[IceCream]

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
      val codec: TypeCodec[IceCream2] = Codec[IceCream2]

      val sundae  = IceCream2("Sundae", 3, cone = false, 1 -> 2)
      val vanilla = IceCream2("Vanilla", 3, cone = true, 2 -> 1)

      val round =
        codec.decode(codec.encode(sundae, ProtocolVersion.DEFAULT), ProtocolVersion.DEFAULT)

      round shouldBe sundae
      round should not be vanilla
    }
  }
}

object UdtCodecSpec {
  @Udt("tests", "ice_cream")
  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  @Udt("tests", "ice_cream")
  case class IceCream2(name: String, numCherries: Int, cone: Boolean, count: (Int, Int))

  @Udt("tests", "ice_cream")
  case class IceCream3(name: String, numCherries: Int, cone: Boolean, @TimeUuid uuid: UUID)
}

class CassandraUdtCodecSpec extends AnyWordSpec with Matchers with CassandraSpec {

  implicit val colMapper: ColumnMapper = SnakeCase

  @Udt("tests", "ice_cream")
  case class IceCream(name: String, numCherries: Int, cone: Boolean)

  "UdtCodec" should {
    "work with Cassandra" in {
      val codec = Codec[IceCream]

      val id = UUID.randomUUID()
      val rs = session.execute(s"SELECT * from udt_table WHERE id = $id")
      rs.one() shouldBe null

      val ice   = IceCream("Vanilla", 2, cone = false)
      val pstmt = session.prepare(s"INSERT INTO udt_table(id, ice) VALUES (?, ?)")
      val bstmt = pstmt
        .bind()
        .set(0, id, uuidCodec)
        .set(1, ice, codec)
      val _ = session.execute(bstmt)

      val rsWithData = session.execute(s"SELECT ice from udt_table WHERE id = $id")
      val row        = rsWithData.one()
      row should not be null

      row.get(0, codec) shouldBe ice
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    session.execute(
      "CREATE TYPE IF NOT EXISTS ice_cream (name TEXT, num_cherries INT, cone BOOLEAN)"
    )
    session.execute("""CREATE TABLE IF NOT EXISTS udt_table(
        |   id      UUID,
        |   ice     ice_cream,
        |   PRIMARY KEY (id)
        |)""".stripMargin)

  }
}
