/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.migrations.example

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

/** A minimal liveness endpoint for a migration job, using the JDK's built-in
  * `HttpServer` so the example takes on no HTTP-server dependency. Bind to port 0 to
  * get an ephemeral port (handy for tests); [[boundPort]] reports the actual port.
  */
final class HealthCheck(host: String, port: Int, path: String) {

  @volatile private var server: Option[HttpServer] = None

  private val contextPath = "/" + path.stripPrefix("/")

  def start(): Unit = {
    val http = HttpServer.create(new InetSocketAddress(host, port), 0)

    http.createContext(
      contextPath,
      (exchange: HttpExchange) => {
        val body = "OK".getBytes(UTF_8)
        exchange.sendResponseHeaders(200, body.length.toLong)
        val out = exchange.getResponseBody
        try out.write(body)
        finally out.close()
      }
    )

    http.start()
    server = Some(http)
  }

  /** The actual bound port, or -1 if not started. */
  def boundPort: Int = server.map(_.getAddress.getPort).getOrElse(-1)

  def stop(): Unit = server.foreach(_.stop(0))
}

object HealthCheck {
  def apply(host: String, port: Int, path: String): HealthCheck = new HealthCheck(host, port, path)
}
