/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec.udt

import java.util.UUID

import scala.jdk.CollectionConverters._

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.internal.core.`type`.DefaultUserDefinedType
import net.nmoncho.helenus.api.ColumnNamingScheme
import net.nmoncho.helenus.utils.CassandraSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `Codec.of` must adapt to the actual field order of a UDT when a statement binding it is
 * prepared. These tests deliberately never call `UnifiedUDTCodec.adapt` by hand, since the
 * prepare path is what's being verified.
 */
class UnifiedUdtCodecAdaptationSpec extends AnyWordSpec with Matchers with CassandraSpec {
  import UnifiedUdtCodecAdaptationSpec._

  private implicit lazy val cqlSession: CqlSession = session

  "A Codec.of codec" should {

    "adapt to a different field order when bound as a later parameter" in {
      implicit val naming: ColumnNamingScheme = ColumnNamingScheme.SnakeCase
      implicit val codec: TypeCodec[Shuffled] = Codec.of[Shuffled](name = "adapt_ice_cream")

      val id  = UUID.randomUUID()
      val ice = Shuffled(numCherries = 2, cone = true, name = "Vanilla")

      "INSERT INTO adapt_table(id, ice) VALUES (?, ?)".toCQL
        .prepare[UUID, Shuffled]
        .execute(id, ice)

      assertStored(id, "Vanilla", 2, cone = true)
      read(id).get("ice", codec) shouldBe ice
    }

    "adapt to a different field order when bound as the first parameter" in {
      implicit val naming: ColumnNamingScheme = ColumnNamingScheme.SnakeCase
      implicit val codec: TypeCodec[Shuffled] = Codec.of[Shuffled](name = "adapt_ice_cream")

      val id  = UUID.randomUUID()
      val ice = Shuffled(numCherries = 5, cone = false, name = "Sundae")

      "UPDATE adapt_table SET ice = ? WHERE id = ?".toCQL
        .prepare[Shuffled, UUID]
        .execute(ice, id)

      assertStored(id, "Sundae", 5, cone = false)
      read(id).get("ice", codec) shouldBe ice
    }

    "keep working when field order already matches" in {
      implicit val naming: ColumnNamingScheme = ColumnNamingScheme.SnakeCase
      implicit val codec: TypeCodec[Aligned]  = Codec.of[Aligned](name = "adapt_ice_cream")

      val id  = UUID.randomUUID()
      val ice = Aligned(name = "Vanilla", numCherries = 2, cone = true)

      "INSERT INTO adapt_table(id, ice) VALUES (?, ?)".toCQL
        .prepare[UUID, Aligned]
        .execute(id, ice)

      assertStored(id, "Vanilla", 2, cone = true)
      read(id).get("ice", codec) shouldBe ice
    }

    // Guards against the naive fix: `accepts` also compares field names, so a positional
    // codec whose names don't line up with the UDT (e.g. no `ColumnNamingScheme` for a
    // snake_case type, as in the Hotels example of the README) isn't accepted either. It has
    // to stay positional instead of being swapped for a name based codec that can't work.
    "stay positional when the order matches but field names don't" in {
      implicit val codec: TypeCodec[Aligned] = Codec.of[Aligned](name = "adapt_ice_cream")

      val id  = UUID.randomUUID()
      val ice = Aligned(name = "Vanilla", numCherries = 2, cone = true)

      "INSERT INTO adapt_table(id, ice) VALUES (?, ?)".toCQL
        .prepare[UUID, Aligned]
        .execute(id, ice)

      assertStored(id, "Vanilla", 2, cone = true)
      read(id).get("ice", codec) shouldBe ice
    }

    // `accepts` also compares field types, so it's false here even though the order is right.
    // Unlike the tests above this one checks the codec directly: a type mismatch like this one
    // can't be told apart from the stored data, the visible effect of switching is that
    // `verifyArity` stops logging its warning.
    "not switch to name based when names and order match but field types don't" in {
      implicit val naming: ColumnNamingScheme = ColumnNamingScheme.SnakeCase

      var switched  = false
      val identical = IdenticalUDTCodec[Aligned]("", "adapt_ice_cream", frozen = true)
      val codec     = new UnifiedUDTCodec[Aligned](
        identical,
        _ => {
          switched = true
          identical
        }
      )

      val udt = new DefaultUserDefinedType(
        CqlIdentifier.fromInternal("adapt_keyspace"),
        CqlIdentifier.fromInternal("adapt_ice_cream"),
        true,
        List("name", "num_cherries", "cone").map(CqlIdentifier.fromInternal).asJava,
        List(DataTypes.ASCII, DataTypes.INT, DataTypes.BOOLEAN).asJava
      )

      withClue("should report the mismatch, so a warning is logged") {
        codec.adaptIfReordered(udt) shouldBe false
      }
      switched shouldBe false
    }
  }

  private def read(id: UUID): Row =
    session.execute(session.prepare("SELECT ice FROM adapt_table WHERE id = ?").bind(id)).one()

  private def assertStored(id: UUID, name: String, numCherries: Int, cone: Boolean): Unit = {
    val udt = read(id).getUdtValue("ice")

    udt.getString("name") shouldBe name
    udt.getInt("num_cherries") shouldBe numCherries
    udt.getBoolean("cone") shouldBe cone
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    executeDDL(
      "CREATE TYPE IF NOT EXISTS adapt_ice_cream (name TEXT, num_cherries INT, cone BOOLEAN)"
    )
    executeDDL(
      "CREATE TABLE IF NOT EXISTS adapt_table(id UUID PRIMARY KEY, ice FROZEN<adapt_ice_cream>)"
    )
  }

  override def afterEach(): Unit = () // tests use unique ids
}

object UnifiedUdtCodecAdaptationSpec {
  case class Aligned(name: String, numCherries: Int, cone: Boolean)
  case class Shuffled(numCherries: Int, cone: Boolean, name: String)
}
