import com.typesafe.tools.mima.core.*

Global / concurrentRestrictions += Tags.limit(Tags.Test, 2)

addCommandAlias(
  "testCoverage",
  "; clean ; coverage; test; coverageAggregate; coverageReport; coverageOff"
)

addCommandAlias(
  "styleFix",
  "; scalafmtSbt; +scalafmtAll; +headerCreateAll; scalafixAll"
)

addCommandAlias(
  "styleCheck",
  "; +scalafmtCheckAll; +headerCheckAll; scalafixAll --check"
)

lazy val root = project
  .in(file("."))
  .settings(basicSettings)
  .settings(
    publish / skip := true,
    mimaFailOnNoPrevious := false,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start())
  )
  .aggregate(docs, core, bench, akka, akkaBusl, flink, monix, pekko, tables, zio)

lazy val basicSettings = Seq(
  organization := "net.nmoncho",
  description := "Helenus is collection of Scala utilities for Apache Cassandra",
  scalaVersion := Dependencies.Version.scala213,
  startYear := Some(2021),
  homepage := Some(url("https://github.com/nMoncho/helenus")),
  // Publish a browsable API reference for every module via javadoc.io (which auto-generates
  // Scaladoc for artifacts on Maven Central). `apiURL` records it in each POM, and
  // `autoAPIMappings` makes our Scaladoc link to dependencies that advertise their own `apiURL`.
  autoAPIMappings := true,
  apiURL := Some(
    url(
      s"https://javadoc.io/doc/net.nmoncho/${moduleName.value}_${scalaBinaryVersion.value}/${version.value}/"
    )
  ),
  licenses := Seq("MIT License" -> new URL("http://opensource.org/licenses/MIT")),
  headerLicense := Some(
    HeaderLicense.MIT("2021", "the original author or authors", HeaderLicenseStyle.SpdxSyntax)
  ),
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
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.StatementOptions.apply"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.tokenCodec"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.net$nmoncho$helenus$api$type$codec$CodecDerivation$_setter_$tokenCodec_="
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.murmur3TokenCodec"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.net$nmoncho$helenus$api$type$codec$CodecDerivation$_setter_$murmur3TokenCodec_="
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.randomTokenCodec"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.net$nmoncho$helenus$api$type$codec$CodecDerivation$_setter_$randomTokenCodec_="
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.byteOrderedTokenCodec"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.type.codec.CodecDerivation.net$nmoncho$helenus$api$type$codec$CodecDerivation$_setter_$byteOrderedTokenCodec_="
    ),
    // `Mapping.withStrictMapping` (opt-in strict mapping) added in v2
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.Mapping.withStrictMapping"
    ),
    ProblemFilters.exclude[ReversedMissingMethodProblem](
      "net.nmoncho.helenus.api.cql.Mapping.withStrictMapping$default$1"
    ),
    ProblemFilters.exclude[DirectMissingMethodProblem](
      "net.nmoncho.helenus.internal.cql.DerivedMapping$DefaultCaseClassDerivedMapping.this"
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
      Dependencies.ossJavaDriver,
      Dependencies.alpakka,
      Dependencies.cassandraUnit
    )
  )
  .dependsOn(core, akka, tables)

lazy val core = project
  .enablePlugins(Antlr4Plugin)
  .settings(basicSettings)
  .settings(
    // The CQL lexer/parser are generated from the `.g4` grammars at build time,
    // rather than committing (and hand-regenerating) the generated Java. This
    // keeps the generated sources in lock-step with the grammar and the ANTLR
    // runtime version, so they can never drift. Only the lexer + parser are used
    // (no listener/visitor).
    Antlr4 / antlr4Version := Dependencies.Version.antlr4,
    Antlr4 / antlr4PackageName := Some("net.nmoncho.helenus.internal.cql"),
    Antlr4 / antlr4GenListener := false,
    Antlr4 / antlr4GenVisitor := false,
    name := "helenus-core",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    crossScalaVersions := List(Dependencies.Version.scala213, Dependencies.Version.scala212),
    libraryDependencies ++= Seq(
      Dependencies.ossJavaDriver % Provided,
      Dependencies.scalaCollectionCompat,
      Dependencies.shapeless,
      Dependencies.slf4j,
      Dependencies.antlr4,
      // Test Dependencies
      "org.scala-lang"           % "scala-compiler" % scalaVersion.value % Test,
      Dependencies.mockito       % Test,
      Dependencies.scalaCheck    % Test,
      Dependencies.scalaTest     % Test,
      Dependencies.scalaTestPlus % Test,
      Dependencies.logback       % Test,
      "net.java.dev.jna" % "jna" % "5.19.0" % Test // Fixes M1 JNA issue
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
      Dependencies.ossJavaDriver,
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
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-akka-busl" % "1.0.0"),
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
  )
  .settings(
    name := "helenus-flink",
    scalaVersion := Dependencies.Version.scala213,
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    Test / fork := true,
    crossScalaVersions := List(Dependencies.Version.scala212, Dependencies.Version.scala213),
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-flink" % "1.7.0"),
    libraryDependencies ++= Seq(
      Dependencies.ossJavaDriver      % "provided,test",
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
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-monix" % "1.7.0"),
    libraryDependencies ++= Seq(
      Dependencies.ossJavaDriver % Provided,
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

lazy val tables = project
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-tables",
    scalaVersion := Dependencies.Version.scala213,
    crossScalaVersions := List(Dependencies.Version.scala213),
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    libraryDependencies ++= Seq(
      Dependencies.ossJavaDriver % Provided
    )
  )

lazy val zio = project
  .settings(basicSettings)
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "helenus-zio",
    scalaVersion := Dependencies.Version.scala213,
    crossScalaVersions := List(Dependencies.Version.scala213, Dependencies.Version.scala212),
    Test / testOptions += Tests.Setup(() => EmbeddedDatabase.start()),
    mimaPreviousArtifacts := Set("net.nmoncho" %% "helenus-zio" % "1.8.1"),
    libraryDependencies ++= Seq(
      Dependencies.ossJavaDriver     % Provided,
      Dependencies.zio               % "provided,test",
      Dependencies.zioStreams        % "provided,test",
      Dependencies.zioStreamsInterop % "provided,test",
      Dependencies.zioTest           % Test,
      Dependencies.zioTestSbt        % Test,
      Dependencies.zioTestMagnolia   % Test
    )
  )
