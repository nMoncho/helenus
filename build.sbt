lazy val dependencies = new {
  object Version {
    val scala213 = "2.13.10"
    val scala212 = "2.12.17"

    val cassandraUnit         = "4.3.1.0"
    val dseJavaDriver         = "4.15.0"
    val scalaCollectionCompat = "2.8.1"
    val scalaJava8Compat      = "1.0.2"
    val shapeless             = "2.3.10"

    // Test Dependencies
    val mockito    = "4.8.0"
    val scalaCheck = "1.17.0"
    val scalaTest  = "3.2.14"
  }

  val cassandraUnit = "org.cassandraunit" % "cassandra-unit"   % Version.cassandraUnit
  val dseJavaDriver = "com.datastax.oss"  % "java-driver-core" % Version.dseJavaDriver
  val scalaReflect  = "org.scala-lang"    % "scala-reflect" // This is Scala version dependent
  val scalaCollectionCompat =
    "org.scala-lang.modules" %% "scala-collection-compat" % Version.scalaCollectionCompat
  val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Version.scalaJava8Compat
  val shapeless        = "com.chuusai"            %% "shapeless"          % Version.shapeless

  val mockito    = "org.mockito"     % "mockito-core" % Version.mockito
  val scalaCheck = "org.scalacheck" %% "scalacheck"   % Version.scalaCheck
  val scalaTest  = "org.scalatest"  %% "scalatest"    % Version.scalaTest
}

addCommandAlias(
  "testCoverage",
  "; clean ; coverage; test; coverageAggregate; coverageReport; coverageOff; clean"
)

addCommandAlias(
  "styleFix",
  "; scalafmtSbt; scalafmtAll; headerCreateAll"
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
  developers := List(
    Developer(
      "nMoncho",
      "Gustavo De Micheli",
      "gustavo.demicheli@gmail.com",
      url("https://github.com/nMoncho")
    )
  ),
  scalaVersion := dependencies.Version.scala213,
  crossScalaVersions := List(dependencies.Version.scala213, dependencies.Version.scala212),
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
  .disablePlugins(ScoverageSbtPlugin)
  .settings(basicSettings)
  .settings(
    publish / skip := true,
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    mdocOut := file("."),
    libraryDependencies ++= Seq(
      dependencies.dseJavaDriver,
      dependencies.cassandraUnit
    )
  )
  .dependsOn(core)

lazy val core = project
  .settings(basicSettings)
  .settings(
    name := "helenus-core",
    libraryDependencies ++= Seq(
      dependencies.dseJavaDriver % Provided,
      dependencies.scalaCollectionCompat,
      dependencies.shapeless,
      // Test Dependencies
      dependencies.cassandraUnit % Test,
      dependencies.mockito       % Test,
      dependencies.scalaCheck    % Test,
      dependencies.scalaTest     % Test,
      "net.java.dev.jna"         % "jna" % "5.12.1" % Test // Fixes M1 JNA issue
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
      if213AndAbove = List(
        dependencies.scalaReflect % dependencies.Version.scala213
      ),
      if212AndBelow = List(
        dependencies.scalaJava8Compat,
        dependencies.scalaReflect % dependencies.Version.scala212
      )
    ),
    coverageMinimum := 85,
    coverageFailOnMinimum := true
  )

lazy val bench = project
  .settings(basicSettings)
  .enablePlugins(JmhPlugin)
  .disablePlugins(ScoverageSbtPlugin)
  .dependsOn(core)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      dependencies.dseJavaDriver,
      dependencies.mockito
    )
  )
