/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.codec.enums

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.reflect.GenericType
import enumeratum.Enum
import enumeratum.EnumEntry
import net.nmoncho.helenus.api.NominalEncoded
import net.nmoncho.helenus.api.`type`.codec.EnumeratumCodecs._
import net.nmoncho.helenus.internal.codec.CodecSpecBase
import net.nmoncho.helenus.internal.codec.enums.EnumeratumNominalCodecSpec.Finger
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EnumeratumNominalCodecSpec extends AnyWordSpec with Matchers with CodecSpecBase[Finger] {

  override protected val codec: TypeCodec[Finger] = Codec[Finger]

  "EnumeratumNominalCodecSpec" should {
    "encode" in {
      encode(Finger.Ring) shouldBe Some("0x52696e67")
      encode(Finger.Index) shouldBe Some("0x496e646578")
      encode(Finger.Little) shouldBe Some("0x4c6974746c65")
    }

    "decode" in {
      decode("0x52696e67") shouldBe Some(Finger.Ring)
      decode("0x496e646578") shouldBe Some(Finger.Index)
      decode("0x4c6974746c65") shouldBe Some(Finger.Little)
    }

    "fail to decode wrong value" in {
      intercept[NoSuchElementException] {
        decode("0x52696e6e")
      }
    }

    "format" in {
      format(Finger.Ring) shouldBe quote("Ring")
      format(Finger.Index) shouldBe quote("Index")
      format(null) shouldBe "NULL"
    }

    "parse" in {
      parse(quote("Ring")) shouldBe Finger.Ring
      parse(quote("Index")) shouldBe Finger.Index
      parse("null") shouldBe null
      parse("") shouldBe null
      parse(null) shouldBe null
    }

    "fail to parse invalid input" in {
      intercept[IllegalArgumentException] {
        parse("not a finger")
      }
    }

    "accept generic type" in {
      codec.accepts(GenericType.of(classOf[Finger])) shouldBe true
      codec.accepts(GenericType.of(classOf[Float])) shouldBe false
    }

    "accept raw type" in {
      codec.accepts(classOf[Finger]) shouldBe true
      codec.accepts(classOf[Float]) shouldBe false
    }

    "accept objects" in {
      codec.accepts(Finger.Index) shouldBe true
      codec.accepts(Double.MaxValue) shouldBe false
    }
  }
}

object EnumeratumNominalCodecSpec {

  @NominalEncoded
  sealed trait Finger extends EnumEntry

  object Finger extends Enum[Finger] {
    override val values: scala.collection.immutable.IndexedSeq[Finger] = findValues

    case object Thumb extends Finger
    case object Index extends Finger
    case object Middle extends Finger
    case object Ring extends Finger
    case object Little extends Finger

    implicit val fingerEnum: Enum[Finger] = this
  }
}
