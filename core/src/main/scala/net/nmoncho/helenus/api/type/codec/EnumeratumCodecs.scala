/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api.`type`.codec

import scala.annotation.unused
import scala.reflect.ClassTag

import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import enumeratum.Enum
import enumeratum.EnumEntry
import net.nmoncho.helenus.api.NominalEncoded
import net.nmoncho.helenus.api.OrdinalEncoded
import net.nmoncho.helenus.internal.codec.enums.EnumeratumNominalCodec
import net.nmoncho.helenus.internal.codec.enums.EnumeratumOrdinalCodec
import shapeless.Annotation

/** Opt-in [[TypeCodec]] support for [[https://github.com/lloydmeta/enumeratum Enumeratum]] enums.
  *
  * Enumeratum is a `Provided` dependency of `helenus-core`, so this support is intentionally kept
  * out of the always-imported `net.nmoncho.helenus._` scope: were these members mixed into that
  * scope, every user (and every downstream module) would need Enumeratum on the classpath even when
  * they don't use it, because implicit resolution would have to load these signatures.
  *
  * Users who map an `enumeratum.Enum` to a CQL column bring Enumeratum in themselves and add:
  * {{{
  * import net.nmoncho.helenus._
  * import net.nmoncho.helenus.api.`type`.codec.EnumeratumCodecs._
  * }}}
  */
trait EnumeratumCodecDerivation {

  /** Builds a new codec for an Enumeratum [[Enum]] by name (its `entryName`) */
  def enumeratumNominalCodec[A <: EnumEntry: ClassTag](enumeratum: Enum[A]): TypeCodec[A] =
    new EnumeratumNominalCodec[A](enumeratum)

  /** Builds a new codec for an Enumeratum [[Enum]] by order (its index in `values`) */
  def enumeratumOrdinalCodec[A <: EnumEntry: ClassTag](enumeratum: Enum[A]): TypeCodec[A] =
    new EnumeratumOrdinalCodec[A](enumeratum)

  /** Summons a by-name codec for an [[Enum]] tagged with [[NominalEncoded]], given an implicit [[Enum]] */
  implicit def nominalEnumeratumCodec[A <: EnumEntry](
      implicit enumeratum: Enum[A],
      tag: ClassTag[A],
      @unused annotation: Annotation[NominalEncoded, A]
  ): TypeCodec[A] =
    enumeratumNominalCodec(enumeratum)

  /** Summons a by-order codec for an [[Enum]] tagged with [[OrdinalEncoded]], given an implicit [[Enum]] */
  implicit def ordinalEnumeratumCodec[A <: EnumEntry](
      implicit enumeratum: Enum[A],
      tag: ClassTag[A],
      @unused annotation: Annotation[OrdinalEncoded, A]
  ): TypeCodec[A] =
    enumeratumOrdinalCodec(enumeratum)
}

object EnumeratumCodecs extends EnumeratumCodecDerivation
