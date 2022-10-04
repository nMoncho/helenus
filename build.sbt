lazy val scala213               = "2.13.8"
lazy val scala212               = "2.12.16"
lazy val supportedScalaVersions = List(scala213, scala212)

addCommandAlias(
  "testCoverage",
  "; clean ; coverage; test; coverageAggregate; coverageReport; coverageOff"
)

lazy val root = project
  .in(file("."))
  .settings(basicSettings)
  .settings(
    publish / skip := true
  )
  .aggregate(docs, core, bench)

lazy val basicSettings = Seq(
  organization := "net.nmoncho",
  description := "Helenus is collection of Scala utilities for Apache Cassandra",
  startYear := Some(2021),
  homepage := Some(url("https://github.com/nMoncho/helenus")),
  licenses := Seq("MIT License" -> new URL("http://opensource.org/licenses/MIT")),
  headerLicense := Some(HeaderLicense.MIT("2021", "the original author or authors")),
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
  scalaVersion := scala213,
  crossScalaVersions := supportedScalaVersions,
  scalacOptions := (Opts.compile.encoding("UTF-8") :+
    Opts.compile.deprecation :+
    Opts.compile.unchecked :+
    "-feature" :+
    "-language:higherKinds"),
  (Test / testOptions) += Tests.Argument("-oF")
)

def crossSetting[A](
    scalaVersion: String,
    if213AndAbove: List[A] = Nil,
    if212AndBelow: List[A] = Nil
): List[A] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, n)) if n >= 13 => if213AndAbove
    case _ => if212AndBelow
  }

lazy val docs = project
  .in(file("helenus-docs"))
  .enablePlugins(MdocPlugin)
  .settings(basicSettings)
  .settings(
    publish / skip := true,
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    mdocOut := file("."),
    libraryDependencies ++= Seq(
      "com.datastax.oss"  % "java-driver-core" % "4.14.1",
      "org.cassandraunit" % "cassandra-unit"   % "4.3.1.0"
    )
  )
  .dependsOn(core)

lazy val core = project
  .settings(basicSettings)
  .settings(
    name := "helenus-core",
    libraryDependencies ++= Seq(
      "com.datastax.oss"        % "java-driver-core"        % "4.14.1"  % Provided,
      "org.scala-lang"          % "scala-reflect"           % "2.13.8",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.8.0",
      "com.chuusai"            %% "shapeless"               % "2.3.9",
      "org.scalatest"          %% "scalatest"               % "3.2.12"  % Test,
      "org.scalacheck"         %% "scalacheck"              % "1.16.0"  % Test,
      "org.cassandraunit"       % "cassandra-unit"          % "4.3.1.0" % Test,
      "org.mockito"             % "mockito-core"            % "4.6.1"   % Test,
      "net.java.dev.jna"        % "jna"                     % "5.12.1"  % Test // Fixes M1 JNA issue
    ),
    scalacOptions ++= crossSetting(
      scalaVersion.value,
      if212AndBelow = List("-language:higherKinds")
    ),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    (Compile / unmanagedSourceDirectories) ++= {
      val sourceDir = (Compile / sourceDirectory).value

      crossSetting(
        scalaVersion.value,
        if213AndAbove = List(sourceDir / "scala-2.13+"),
        if212AndBelow = List(sourceDir / "scala-2.13-")
      )
    },
    libraryDependencies ++= crossSetting(
      scalaVersion.value,
      if212AndBelow = List(
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1"
      )
    ),
    coverageMinimum := 85,
    coverageFailOnMinimum := true
  )

lazy val bench = project
  .settings(basicSettings)
  .enablePlugins(JmhPlugin)
  .dependsOn(core)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.datastax.oss" % "java-driver-core" % "4.14.1"
    )
  )
