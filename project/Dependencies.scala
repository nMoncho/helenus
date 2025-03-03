import sbt.*

object Dependencies {
  object Version {
    val scala213 = "2.13.16" // JLine fails on this version, downgrade to 2.13.12 to use REPL
    val scala212 = "2.12.20"

    val cassandraUnit         = "4.3.1.0"
    val dseJavaDriver         = "4.17.0"
    val scalaCollectionCompat = "2.13.0"
    val scalaJava8Compat      = "1.0.2"
    val shapeless             = "2.3.13"
    val slf4j                 = "2.0.16"

    val akka    = "2.6.20" // 2.7 changed to business license
    val alpakka = "4.0.0" // 5.x changed to business license

    val akkaBusl    = "2.8.5"
    val alpakkaBusl = "6.0.2"

    val flink = "1.18.1"

    val monix = "3.4.1"

    val pekkoConnector = "1.1.0"
    val pekkoTestKit   = "1.1.3"

    val zio               = "2.1.15"
    val zioStreamsInterop = "2.0.2"

    // Test Dependencies
    val mockito       = "5.14.2"
    val scalaCheck    = "1.18.1"
    val scalaTest     = "3.2.19"
    val scalaTestPlus = "3.2.18.0"
    val logback       = "1.5.16"
  }

  // 'core' dependencies
  val cassandraUnit = "org.cassandraunit" % "cassandra-unit"   % Version.cassandraUnit
  val dseJavaDriver = "com.datastax.oss"  % "java-driver-core" % Version.dseJavaDriver
  val scalaReflect  = "org.scala-lang"    % "scala-reflect" // This is Scala version dependent
  val scalaCollectionCompat =
    "org.scala-lang.modules" %% "scala-collection-compat" % Version.scalaCollectionCompat
  val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Version.scalaJava8Compat
  val shapeless        = "com.chuusai"            %% "shapeless"          % Version.shapeless
  val slf4j            = "org.slf4j"               % "slf4j-api"          % Version.slf4j

  // 'akka' dependencies
  val alpakka     = "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % Version.alpakka
  val akkaTestKit = "com.typesafe.akka"  %% "akka-testkit"                  % Version.akka

  // 'akka-busl' dependencies
  val alpakkaBusl = "com.lightbend.akka" %% "akka-stream-alpakka-cassandra" % Version.alpakkaBusl
  val akkaTestKitBusl = "com.typesafe.akka" %% "akka-testkit" % Version.akkaBusl

  // 'flink' dependencies
  val flinkCore          = "org.apache.flink" % "flink-core"           % Version.flink
  val flinkStreamingJava = "org.apache.flink" % "flink-streaming-java" % Version.flink
  val flinkConnectorBase = "org.apache.flink" % "flink-connector-base" % Version.flink
  val flinkTestUtils     = "org.apache.flink" % "flink-test-utils"     % Version.flink

  // 'monix' dependencies
  val monix         = "io.monix" %% "monix"          % Version.monix
  val monixReactive = "io.monix" %% "monix-reactive" % Version.monix

  // 'pekko' dependencies
  val pekkoConnector = "org.apache.pekko" %% "pekko-connectors-cassandra" % Version.pekkoConnector
  val pekkoTestKit   = "org.apache.pekko" %% "pekko-testkit"              % Version.pekkoTestKit

  // 'zio' dependencies
  val zio               = "dev.zio" %% "zio"                         % Version.zio
  val zioStreams        = "dev.zio" %% "zio-streams"                 % Version.zio
  val zioStreamsInterop = "dev.zio" %% "zio-interop-reactivestreams" % Version.zioStreamsInterop
  val zioTest           = "dev.zio" %% "zio-test"                    % Version.zio
  val zioTestSbt        = "dev.zio" %% "zio-test-sbt"                % Version.zio
  val zioTestMagnolia   = "dev.zio" %% "zio-test-magnolia"           % Version.zio

  val mockito       = "org.mockito"        % "mockito-core"    % Version.mockito
  val scalaCheck    = "org.scalacheck"    %% "scalacheck"      % Version.scalaCheck
  val scalaTest     = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val scalaTestPlus = "org.scalatestplus" %% "scalacheck-1-17" % Version.scalaTestPlus
  val logback       = "ch.qos.logback"     % "logback-classic" % Version.logback
}
