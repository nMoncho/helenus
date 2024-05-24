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

package net.nmoncho.helenus.flink.sink

import java.time.Duration

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.CqlSessionBuilder
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader
import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

final class CassandraSink[T](underlying: Either[SingleOutputStreamOperator[T], DataStreamSink[T]]) {

  /** Sets the name of this sink. This name is used by the visualization and logging during
    * runtime.
    *
    * @return The named sink.
    */
  def name(name: String): CassandraSink[T] = withUnderlying(
    _.getTransformation.setName(name),
    _.getLegacyTransformation.setName(name)
  )

  /** Sets an ID for this operator.
    *
    * <p>The specified ID is used to assign the same operator ID across job submissions (for
    * example when starting a job from a savepoint).
    *
    * <p><strong>Important</strong>: this ID needs to be unique per transformation and job.
    * Otherwise, job submission will fail.
    *
    * @param uid The unique user-specified ID of this transformation.
    * @return The operator with the specified ID.
    */
  def uid(uid: String): CassandraSink[T] = withUnderlying(
    _.getTransformation.setUid(uid),
    _.getLegacyTransformation.setUid(uid)
  )

  /** Sets an user provided hash for this operator. This will be used AS IS the create the
    * JobVertexID.
    *
    * <p>The user provided hash is an alternative to the generated hashes, that is considered when
    * identifying an operator through the default hash mechanics fails (e.g. because of changes
    * between Flink versions).
    *
    * <p><strong>Important</strong>: this should be used as a workaround or for trouble shooting.
    * The provided hash needs to be unique per transformation and job. Otherwise, job submission
    * will fail. Furthermore, you cannot assign user-specified hash to intermediate nodes in an
    * operator chain and trying so will let your job fail.
    *
    * <p>A use case for this is in migration between Flink versions or changing the jobs in a way
    * that changes the automatically generated hashes. In this case, providing the previous hashes
    * directly through this method (e.g. obtained from old logs) can help to reestablish a lost
    * mapping from states to their target operator.
    *
    * @param uidHash The user provided hash for this operator. This will become the JobVertexID,
    *                which is shown in the logs and web ui.
    * @return The operator with the user provided hash.
    */
  def setUidHash(uidHash: String): CassandraSink[T] = withUnderlying(
    _.getTransformation.setUidHash(uidHash),
    _.getLegacyTransformation.setUidHash(uidHash)
  )

  /** Sets the parallelism for this sink. The degree must be higher than zero.
    *
    * @param parallelism The parallelism for this sink.
    * @return The sink with set parallelism.
    */
  def setParallelism(parallelism: Int): CassandraSink[T] = withUnderlying(
    _.setParallelism(parallelism),
    _.setParallelism(parallelism)
  )

  /** Turns off chaining for this operator so thread co-location will not be used as an
    * optimization.
    *
    * <p>Chaining can be turned off for the whole job by [[org.apache.flink.streaming.api.environment.StreamExecutionEnvironment#disableOperatorChaining()]]
    * however it is not advised for performance considerations.
    *
    * @return The sink with chaining disabled
    */
  def disableChaining(): CassandraSink[T] = withUnderlying(
    _.disableChaining(),
    _.disableChaining()
  )

  /** Sets the slot sharing group of this operation. Parallel instances of operations that are in
    * the same slot sharing group will be co-located in the same TaskManager slot, if possible.
    *
    * <p>Operations inherit the slot sharing group of input operations if all input operations are
    * in the same slot sharing group and no slot sharing group was explicitly specified.
    *
    * <p>Initially an operation is in the default slot sharing group. An operation can be put into
    * the default group explicitly by setting the slot sharing group to `default`.
    *
    * @param slotSharingGroup The slot sharing group name.
    */
  def slotSharingGroup(slotSharingGroup: String): CassandraSink[T] = withUnderlying(
    _.getTransformation.setSlotSharingGroup(slotSharingGroup),
    _.getLegacyTransformation.setSlotSharingGroup(slotSharingGroup)
  )

  private def withUnderlying(
      left: SingleOutputStreamOperator[T] => Unit,
      right: DataStreamSink[T] => Unit
  ): CassandraSink[T] = {
    underlying.fold(left, right)
    this
  }
}

object CassandraSink {

  val log: Logger = LoggerFactory.getLogger(classOf[CassandraSink[_]])

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
