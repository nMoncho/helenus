import sbt.*

object Dependencies {
  object Version {
    val scala213 = "2.13.14" // JLine fails on this version, downgrade to 2.13.12 to use REPL
    val scala212 = "2.12.19"

    val cassandraUnit         = "4.3.1.0"
    val dseJavaDriver         = "4.17.0"
    val scalaCollectionCompat = "2.12.0"
    val scalaJava8Compat      = "1.0.2"
    val shapeless             = "2.3.12"
    val slf4j                 = "2.0.13"

    val akka    = "2.6.20" // 2.7 changed to business license
    val alpakka = "4.0.0" // 5.x changed to business license

    val akkaBusl    = "2.8.5"
    val alpakkaBusl = "6.0.2"

    val flink = "1.18.1"

    val pekkoConnector = "1.0.2"
    val pekkoTestKit   = "1.0.3"

    // Test Dependencies
    val mockito       = "5.12.0"
    val scalaCheck    = "1.18.0"
    val scalaTest     = "3.2.19"
    val scalaTestPlus = "3.2.18.0"
    val logback       = "1.5.7"
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

  // 'pekko' dependencies
  val pekkoConnector = "org.apache.pekko" %% "pekko-connectors-cassandra" % Version.pekkoConnector
  val pekkoTestKit   = "org.apache.pekko" %% "pekko-testkit"              % Version.pekkoTestKit

  val mockito       = "org.mockito"        % "mockito-core"    % Version.mockito
  val scalaCheck    = "org.scalacheck"    %% "scalacheck"      % Version.scalaCheck
  val scalaTest     = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val scalaTestPlus = "org.scalatestplus" %% "scalacheck-1-17" % Version.scalaTestPlus
  val logback       = "ch.qos.logback"     % "logback-classic" % Version.logback
}
