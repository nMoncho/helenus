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

package net.nmoncho.helenus.flink.source

import java.time.Duration

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.typesafe.config.ConfigFactory

object CassandraSource {

  case class Config(
      configPath: String,
      config: com.typesafe.config.Config,
      maxConcurrentRequests: Int,
      maxConcurrentRequestsTimeout: Duration,
      failureHandler: Throwable => Unit
  ) {
    require(!configPath.isBlank && config.hasPath(configPath), "Config path needs to be available")
    require(maxConcurrentRequests > 0, "Max concurrent requests is expected to be positive")
    require(maxConcurrentRequestsTimeout != null, "Max concurrent requests timeout cannot be null")
    require(
      !maxConcurrentRequestsTimeout.isNegative,
      "Max concurrent requests timeout is expected to be positive"
    )

    def session(): CqlSession =
      new CqlSessionBuilder()
        .withConfigLoader(new DefaultDriverConfigLoader(() => config.getConfig(configPath)))
        .build()

  }

  object Config {
    val DefaultConfigPath: String              = DefaultDriverConfigLoader.DEFAULT_ROOT_PATH
    val MaxConcurrentRequests: Int             = Int.MaxValue
    val MaxConcurrentRequestsTimeout: Duration = Duration.ofMillis(Long.MaxValue)
    val NoOpFailureHandler: Throwable => Unit  = _ => ()

    def apply(): Config = Config(
      configPath                   = DefaultConfigPath,
      config                       = ConfigFactory.load(),
      maxConcurrentRequests        = MaxConcurrentRequests,
      maxConcurrentRequestsTimeout = MaxConcurrentRequestsTimeout,
      failureHandler               = NoOpFailureHandler
    )
  }
}
