import ReleaseTransformations._

val commonName     = "db-async-common"
val postgresqlName = "postgresql-async"
val mysqlName      = "mysql-async"
val nettyVersion   = "4.1.66.Final"

def specs2Dependency(scalaVersion: String) = {
  Seq(
    "org.specs2" %% "specs2-core"  % specs2Version(scalaVersion) % Test,
    "org.specs2" %% "specs2-junit" % specs2Version(scalaVersion) % Test
  )
}
val logbackDependency = "ch.qos.logback" % "logback-classic" % "1.2.3" % Test

def specs2Version(scalaVersion: String) = {
  if (scalaVersion.startsWith("2."))
    "4.10.6"
  else "5.0.0-RC-15"
}

lazy val root = (project in file("."))
  .settings(baseSettings: _*)
  .settings(
    name            := "db-async-base",
    publish         := {},
    publishLocal    := {},
    publishArtifact := false
  )
  .aggregate(common, postgresql, mysql)

lazy val common = (project in file("db-async-common"))
  .settings(baseSettings: _*)
  .settings(
    name := commonName,
    libraryDependencies ++= commonDependencies(scalaVersion.value)
  )

lazy val postgresql = (project in file("postgresql-async"))
  .settings(baseSettings: _*)
  .settings(
    name := postgresqlName,
    libraryDependencies ++= implementationDependencies(scalaVersion.value)
  )
  .dependsOn(common)

lazy val mysql = (project in file("mysql-async"))
  .settings(baseSettings: _*)
  .settings(
    name := mysqlName,
    libraryDependencies ++= implementationDependencies(scalaVersion.value)
  )
  .dependsOn(common)

def commonDependencies(scalaVersion: String) = Seq(
  "org.slf4j"                % "slf4j-api"               % "1.7.29",
  "joda-time"                % "joda-time"               % "2.10.5",
  "org.joda"                 % "joda-convert"            % "2.2.1",
  "io.netty"                 % "netty-codec"             % nettyVersion,
  "io.netty"                 % "netty-handler"           % nettyVersion,
  "org.javassist"            % "javassist"               % "3.26.0-GA",
  "org.scala-lang.modules"  %% "scala-collection-compat" % "2.5.0",
  "com.google.code.findbugs" % "jsr305"                  % "3.0.1" % Provided
) ++ specs2Dependency(scalaVersion)

def implementationDependencies(scalaVersion: String) =
  specs2Dependency(scalaVersion) ++ Seq(
    logbackDependency
  )

val baseSettings = Seq(
  crossScalaVersions := Seq("2.11.12", "2.12.14", "2.13.6", "3.0.2"),
  (Test / testOptions) += Tests.Argument("sequential"),
  (Test / fork) := true,
  scalaVersion  := "2.13.6",
  scalacOptions :=
    Opts.compile.encoding("UTF8")
      :+ Opts.compile.deprecation
      :+ Opts.compile.unchecked
      :+ "-feature"
      :+ "-Ydelambdafy:method"
      :+ "-Xsource:3",
  (Test / testOptions) += Tests.Argument(TestFrameworks.Specs2, "sequential"),
  (doc / scalacOptions) := Seq(
    s"-doc-external-doc:scala=https://www.scala-lang.org/files/archive/api/${scalaVersion.value}/"
  ),
  javacOptions := Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF8"),
  (Test / javaOptions) ++= Seq("-Dio.netty.leakDetection.level=paranoid"),
  organization      := "com.github.postgresql-async",
  parallelExecution := false
) ++ publishSettings

lazy val publishSettings = Seq(
  // Add sonatype repository settings
  publishTo := Some(
    if (isSnapshot.value)
      Opts.resolver.sonatypeSnapshots
    else
      Opts.resolver.sonatypeStaging
  ),
  releaseCrossBuild             := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    setNextVersion,
    commitNextVersion,
    releaseStepCommand("sonatypeReleaseAll"),
    pushChanges
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/postgresql-async/postgresql-async"),
      "git@github.com:postgresql-async/postgresql-async.git"
    )
  ),
  developers += Developer(
    "jilen",
    "jilen",
    "jilen.zhang@gmail.com",
    url("https://github.com/jilen")
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  pomIncludeRepository := (_ => false),
  homepage := Some(url("https://github.com/postgresql-async/postgresql-async"))
)

(ThisBuild / scalafmtOnCompile) := true
(Compile / compile) := {
  (Compile / compile).dependsOn(Compile / scalafmtSbt).value
}
