import Dependencies._

ThisBuild / version := whipsawVersion
name := "whipsaw"

publishMavenStyle := false

scalaVersion := scalaVsn

scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-unchecked",
  "-deprecation"
  //, "-Xfatal-warnings"
)

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.2.0",
  "org.scalatest" %% "scalatest" % "3.2.0" % "test",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.30" % "test"
  //, "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  //, "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  //, "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion
)

dependencyOverrides in ThisBuild += "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
dependencyOverrides in ThisBuild += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
dependencyOverrides in ThisBuild += "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion

// play-json 2.9.0 gets pulled in by default, but this causes a NoSuchMethod error when
// running in Iterable.  Forcing this down to the min version.
dependencyOverrides in ThisBuild += "com.typesafe.play" %% "play-json" % "2.7.4"

lazy val api = project in file("api")

lazy val `workload-reactive-mongo-impl` =
  (project in file("libs/workloads/reactive-mongo-impl")).dependsOn(api)

lazy val `workload-in-memory-impl` =
  (project in file("libs/workloads/in-memory-impl")).dependsOn(api)

`workload-in-memory-impl` / publish / skip := true

lazy val `engine-local-functions` =
  (project in file("libs/engines/local-functions")).dependsOn(api)

lazy val `play-app` = (project in file("play-app"))
  .enablePlugins(PlayScala)
  .dependsOn(api, `workload-reactive-mongo-impl`, `engine-local-functions`)
  .aggregate(api, `workload-reactive-mongo-impl`, `engine-local-functions`)

`play-app` / publish / skip := true

lazy val root = (project in file("."))
  .dependsOn(api, `workload-reactive-mongo-impl`, `engine-local-functions`)
  .aggregate(
    api,
    `workload-reactive-mongo-impl`,
    `engine-local-functions`,
    `play-app`
  )

root / publish / skip := true

//
//          S   O   N   A   T   Y   P   E  
//
//      P   U   B   L   I   S   H   I   N   G
//

ThisBuild / organization := "io.github.thats-what-im-talking-about"
ThisBuild / organizationName := "TWITA"
ThisBuild / organizationHomepage := Some(url("http://gihub.com/thats-what-im-talking-about"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/thats-what-im-talking-about/whipsaw"),
    "scm:git@github.com:thats-what-im-talking-about/whipsaw.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "bplawler",
    name  = "Brian Lawler",
    email = "bplawler@gmail.com",
    url   = url("https://github.com/bplawler")
  )
)

ThisBuild / description := "Generic Workload Management framework"
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/thats-what-im-talking-about/whipsaw"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true

