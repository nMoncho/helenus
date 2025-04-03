/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.api

/** Marker annotation for an [[Enumeration]] that wants to be encoded by order
  */
case class OrdinalEncoded() extends scala.annotation.StaticAnnotation

/** Marker annotation for an [[Enumeration]] that wants to be encoded by name
  */
case class NominalEncoded() extends scala.annotation.StaticAnnotation
