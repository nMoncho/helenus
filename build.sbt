import com.typesafe.tools.mima.core.{
  DirectMissingMethodProblem,
  IncompatibleResultTypeProblem,
  MissingClassProblem,
  ProblemFilters,
  ReversedMissingMethodProblem
}

Global / concurrentRestrictions += Tags.limit(Tags.Test, 2)

addCommandAlias(
  "testCoverage",
  "; clean ; coverage; test; coverageAggregate; coverageReport; coverageOff"
)

addCommandAlias(
  "styleFix",
  "; scalafmtSbt; scalafmtAll; headerCreateAll; scalafixAll"
)

addCommandAlias(
  "styleCheck",
  "; scalafmtCheckAll; headerCheckAll; scalafixAll --check"
)

lazy val root = project
  .in(file("."))
  .settings(basicSettings)
  .settings(
    publish / skip := true,
    mimaFailOnNoPrevious := false,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start())
  )
  .aggregate(docs, core, bench, akka, akkaBusl, pekko, flink, monix)

lazy val basicSettings = Seq(
  organization := "net.nmoncho",
  description := "Helenus is collection of Scala utilities for Apache Cassandra",
  scalaVersion := Dependencies.Version.scala213,
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
  scalacOptions := (Opts.compile.encoding("UTF-8") :+
    Opts.compile.deprecation :+
    Opts.compile.unchecked :+
    "-feature" :+
    "-Ywarn-unused" :+
    "-language:higherKinds" :+
    "-Xlog-implicits"),
  (Test / testOptions) += Tests.Argument("-oF"),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.ScalaPreparedStatement.as"
    ),
    ProblemFilters.exclude[IncompatibleResultTypeProblem]("net.nmoncho.helenus.internal.cql.*.as"),
    ProblemFilters.exclude[MissingClassProblem](
      "net.nmoncho.helenus.pekko.package$*Akka*"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.pekko.package.*Akka*"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.ScalaPreparedStatement.tag"
    ),
    ProblemFilters.exclude[MissingClassProblem](
      "net.nmoncho.helenus.api.cql.ScalaPreparedStatement$BoundStatementOps"
    ),
    ProblemFilters.exclude[MissingClassProblem](
      "net.nmoncho.helenus.api.cql.ScalaPreparedStatement$BoundStatementOps$"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.StatementOptions.copy*"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.StatementOptions.this"
    ),
    ProblemFilters.exclude[IncompatibleResultTypeProblem](
      "net.nmoncho.helenus.api.cql.StatementOptions.copy*"
    )
  )
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
    mimaFailOnNoPrevious := false,
    mdocVariables := Map(
      "VERSION" -> version.value
    ),
    mdocOut := file("."),
    libraryDependencies ++= Seq(
      Dependencies.dseJavaDriver,
      Dependencies.cassandraUnit
    )
  )
  .dependsOn(core)

lazy val core = project
  .settings(basicSettings)
  .settings(
    name := "helenus-core",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    crossScalaVersions := List(Dependencies.Version.scala213, Dependencies.Version.scala212),
    libraryDependencies ++= Seq(
      Dependencies.dseJavaDriver % Provided,
      Dependencies.scalaCollectionCompat,
      Dependencies.shapeless,
      Dependencies.slf4j,
      // Test Dependencies
      Dependencies.mockito       % Test,
      Dependencies.scalaCheck    % Test,
      Dependencies.scalaTest     % Test,
      Dependencies.scalaTestPlus % Test,
      Dependencies.logback       % Test,
      "net.java.dev.jna"         % "jna" % "5.15.0" % Test // Fixes M1 JNA issue
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
        Dependencies.scalaReflect % Dependencies.Version.scala213
      ),
      if212AndBelow = List(
        Dependencies.scalaJava8Compat,
        Dependencies.scalaReflect % Dependencies.Version.scala212
      )
    ),
    coverageMinimumStmtTotal := 70,
    coverageMinimumBranchTotal := 70,
    coverageFailOnMinimum := true,
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-core" % "1.0.0")
  )

lazy val bench = project
  .settings(basicSettings)
  .enablePlugins(JmhPlugin)
  .disablePlugins(ScoverageSbtPlugin)
  .dependsOn(core)
  .settings(
    publish / skip := true,
    mimaFailOnNoPrevious := false,
    libraryDependencies ++= Seq(
      Dependencies.dseJavaDriver,
      Dependencies.mockito
    )
  )

lazy val akka = project
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-akka",
    scalaVersion := Dependencies.Version.scala213,
    crossScalaVersions := List(Dependencies.Version.scala213),
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-akka" % "1.0.0"),
    // 5.x changed to business license
    dependencyUpdatesFilter -= moduleFilter(organization = "com.lightbend.akka"),
    // 2.7.x changed to business license
    dependencyUpdatesFilter -= moduleFilter(organization = "com.typesafe.akka"),
    libraryDependencies ++= Seq(
      Dependencies.alpakka     % "provided,test",
      Dependencies.akkaTestKit % Test,
      // Adding this until Alpakka aligns version with Akka TestKit
      "com.typesafe.akka" %% "akka-stream" % Dependencies.Version.akka
    )
  )

lazy val akkaBusl = project
  .in(file("akka-busl"))
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-akka-busl",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    crossScalaVersions := List(Dependencies.Version.scala213),
    mimaFailOnNoPrevious := false,
    libraryDependencies ++= Seq(
      Dependencies.alpakkaBusl     % "provided,test",
      Dependencies.akkaTestKitBusl % Test,
      // Adding this until Alpakka aligns version with Akka TestKit
      "com.typesafe.akka" %% "akka-stream" % Dependencies.Version.akkaBusl
    )
  )
lazy val flink = project
  .settings(basicSettings)
  .dependsOn(
    core % "compile->compile;test->test"
  ) // FIXME there are several excluded or shaded dependencies from the Java Driver, why?
  .settings(
    name := "helenus-flink",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    Test / fork := true,
    crossScalaVersions := List(Dependencies.Version.scala212, Dependencies.Version.scala213),
    mimaFailOnNoPrevious := false,
    libraryDependencies ++= Seq(
      Dependencies.dseJavaDriver,
      Dependencies.flinkCore          % "provided,test",
      Dependencies.flinkStreamingJava % "provided,test",
      Dependencies.flinkConnectorBase % "provided,test",
      Dependencies.flinkTestUtils     % "provided,test"
    )
  )

lazy val monix = project
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-monix",
    scalaVersion := Dependencies.Version.scala213,
    crossScalaVersions := List(Dependencies.Version.scala213, Dependencies.Version.scala212),
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    mimaFailOnNoPrevious := false,
    libraryDependencies ++= Seq(
      Dependencies.dseJavaDriver % Provided,
      Dependencies.monix         % "provided,test",
      Dependencies.monixReactive % "provided,test"
    )
  )

lazy val pekko = project
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-pekko",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-pekko" % "1.0.0"),
    crossScalaVersions := List(Dependencies.Version.scala213),
    libraryDependencies ++= Seq(
      Dependencies.pekkoConnector % "provided,test",
      Dependencies.pekkoTestKit   % Test,
      // Adding this until Alpakka aligns version with Pekko TestKit
      "org.apache.pekko" %% "pekko-stream" % Dependencies.Version.pekkoTestKit
    )
  )
