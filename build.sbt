import Dependencies._

name := "whipsaw"

publishMavenStyle := false

scalaVersion := scalaVsn

scalacOptions in ThisBuild ++= Seq(
  "-feature",
  "-unchecked",
  "-deprecation"
  //, "-Xfatal-warnings"
)


//
//              G   I   T   H   U   B      
//
//      P   U   B   L   I   S   H   I   N   G
//

githubOwner in ThisBuild := "thats-what-im-talking-about"
githubRepository in ThisBuild := "whipsaw"
githubTokenSource in ThisBuild := TokenSource.GitConfig("github.token")

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.2.0" % "test",
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

//inThisBuild(List(
  //organization := "io.github.thats-what-im-talking-about",
  //homepage := Some(url("http://gihub.com/thats-what-im-talking-about")),
  //licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  //developers := List(
    //Developer(
      //"bplawler",
      //"Brian Lawler",
      //"bplawler@gmail.com",
      //url("https://github.com/bplawler")
    //)
  //)
//))
