import Dependencies._

name := "whipsaw-api"

scalaVersion := scalaVsn

libraryDependencies ++= Seq(
  "io.github.thats-what-im-talking-about" %% "dominion-api" % dominionVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion
)
