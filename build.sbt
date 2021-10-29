import ReleaseTransformations._

val commonName     = "db-async-common"
val postgresqlName = "postgresql-async"
val mysqlName      = "mysql-async"
val nettyVersion   = "4.1.66.Final"

def testDependency(scalaVersion: String) = {
  Seq(
    "org.scalatest" %% "scalatest"    % "3.2.10" % Test,
    "org.mockito"    % "mockito-core" % "4.0.0"  % Test,
    "org.slf4j"      % "slf4j-simple" % "1.7.29" % Test
  )
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
    name := postgresqlName
  )
  .dependsOn(common % "compile->compile;test->test")

lazy val mysql = (project in file("mysql-async"))
  .settings(baseSettings: _*)
  .settings(
    name := mysqlName
  )
  .dependsOn(common % "compile->compile;test->test")

def commonDependencies(scalaVersion: String) = Seq(
  "org.slf4j"                % "slf4j-api"               % "1.7.29",
  "joda-time"                % "joda-time"               % "2.10.5",
  "org.joda"                 % "joda-convert"            % "2.2.1",
  "io.netty"                 % "netty-codec"             % nettyVersion,
  "io.netty"                 % "netty-handler"           % nettyVersion,
  "org.javassist"            % "javassist"               % "3.26.0-GA",
  "org.scala-lang.modules"  %% "scala-collection-compat" % "2.5.0",
  "com.google.code.findbugs" % "jsr305"                  % "3.0.2" % Provided
) ++ testDependency(scalaVersion)

def scalacOpts(v: String): Seq[String] = {
  val base = Opts.compile.encoding(
    "UTF8"
  ) :+ Opts.compile.deprecation :+ Opts.compile.unchecked :+ "-feature"
  if (v.startsWith("3.")) {
    base
  } else if (v.startsWith("2.11")) {
    base ++ Seq(
      "-Xmax-classfile-name",
      "78",
      "-Xsource:3",
      "-Ydelambdafy:method"
    )
  } else { //  2.12.x & 2.13.x
    base ++ Seq(
      Opts.compile.deprecation,
      Opts.compile.unchecked,
      "-Xsource:3"
    )
  }
}

val baseSettings = Seq(
  crossScalaVersions := Seq("2.11.12", "2.12.14", "2.13.6", "3.0.2"),
  (Test / fork)      := true,
  scalaVersion       := "2.13.6",
  scalacOptions      := scalacOpts(scalaVersion.value),
  (doc / scalacOptions) := Seq(
    s"-doc-external-doc:scala=https://www.scala-lang.org/files/archive/api/${scalaVersion.value}/"
  ),
  javacOptions := Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF8"),
  (Test / javaOptions) ++= Seq("-Dio.netty.leakDetection.level=paranoid"),
  organization               := "com.github.postgresql-async",
  (Test / parallelExecution) := false
) ++ publishSettings

lazy val publishSettings = Seq(
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/postgresql-async/postgresql-async"),
      "git@github.com:postgresql-async/postgresql-async.git"
    )
  ),
  releaseProcess := Seq[ReleaseStep]( // release was run by github action, just make a tag here
    checkSnapshotDependencies,
    inquireVersions,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion,
    pushChanges
  ),
  developers += Developer(
    "jilen",
    "jilen",
    "jilen.zhang@gmail.com",
    url("https://github.com/jilen")
  ),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/postgresql-async/postgresql-async"))
)

(ThisBuild / scalafmtOnCompile) := true
(Compile / compile) := {
  (Compile / compile).dependsOn(Compile / scalafmtSbt).value
}
