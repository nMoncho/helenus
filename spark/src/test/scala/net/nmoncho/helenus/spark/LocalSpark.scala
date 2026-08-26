/*
 * Copyright 2021 the original author or authors
 *
 * SPDX-License-Identifier: MIT
 */

package net.nmoncho.helenus.spark

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

/** A single local `SparkSession` shared across the module's Spark specs.
  *
  * Only one `SparkContext` can be live per JVM, and `getOrCreate` hands every caller the
  * same one; if each spec built and then `stop()`ed its own, one suite would tear the
  * context down while another still used it. So the session is created once, lazily, and
  * left running for the JVM to reclaim at exit, specs reference it and never stop it.
  */
object LocalSpark {

  lazy val session: SparkSession = {
    val conf = new SparkConf()
      .setMaster("local[2]")
      .setAppName("helenus-spark-tests")
      .set("spark.ui.enabled", "false")
      .set("spark.driver.host", "localhost")
      .set("spark.driver.bindAddress", "localhost")
      .set("spark.cassandra.connection.host", "localhost")
      .set("spark.cassandra.connection.port", "9142")
      .set("spark.cassandra.connection.localDC", "datacenter1")

    SparkSession.builder().config(conf).getOrCreate()
  }
}
