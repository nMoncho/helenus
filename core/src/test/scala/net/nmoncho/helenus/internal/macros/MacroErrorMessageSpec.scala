/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus
package internal.macros

import net.nmoncho.helenus.api.ColumnNamingScheme
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import shapeless.test.illTyped

/** The derivation macros must abort with an actionable message (instead of a raw
  * `scala.MatchError` from the compiler internals) when handed something other
  * than a simple `_.field` selector.
  */
class MacroErrorMessageSpec extends AnyWordSpec with Matchers {
  import MacroErrorMessageSpec._

  // Required by `udtFromFields` inside the `illTyped` snippet below; kept as a
  // (public) member so `-Ywarn-unused` does not flag it (the usage sits inside
  // the type-checked string, which the enclosing compilation cannot see).
  implicit val naming: ColumnNamingScheme = ColumnNamingScheme.Default

  "Codec.udtFromFields" should {
    "reject a non field-selector lambda with a helpful message" in {
      illTyped(
        """Codec.udtFromFields[IceCream]("", "", true)((_: IceCream) => 42)""",
        "(?s).*simple field selector.*"
      )
    }
  }
}

object MacroErrorMessageSpec {
  final case class IceCream(name: String, numCherries: Int, cone: Boolean)
}
