import Dependencies._

ThisBuild / organization := "twita"
ThisBuild / version := whipsawVersion
name := "whipsaw"

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq(
    "-feature"
  , "-unchecked"
  , "-deprecation"
  //, "-Xfatal-warnings"
)

libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.2.0"
  , "org.scalatest" %% "scalatest" % "3.2.0" % "test"
  , "org.slf4j" % "log4j-over-slf4j" % "1.7.30" % "test"
  //, "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
  //, "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  //, "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion
)

lazy val api = project in file("api")

lazy val `workload-reactive-mongo-impl` = (project in file("libs/workloads/reactive-mongo-impl")).dependsOn(api)

lazy val `workload-in-memory-impl` = (project in file("libs/workloads/in-memory-impl")).dependsOn(api)

lazy val `engine-local-functions` = (project in file("libs/engines/local-functions")).dependsOn(api)

lazy val `play-app` = (project in file("play-app"))
  .enablePlugins(PlayScala)
  .dependsOn( api
    , `workload-reactive-mongo-impl`
    , `engine-local-functions`
  )
  .aggregate( api
    , `workload-reactive-mongo-impl`
    , `engine-local-functions`
  )

lazy val root = (project in file("."))
  .dependsOn( api
            , `workload-reactive-mongo-impl`
            , `engine-local-functions`
            )
  .aggregate( api
            , `workload-reactive-mongo-impl`
            , `engine-local-functions`
            , `play-app`
            )
