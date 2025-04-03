/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
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
