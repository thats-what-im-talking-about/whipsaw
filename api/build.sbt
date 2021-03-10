import Dependencies._

name := "whipsaw-api"

scalaVersion := scalaVsn

libraryDependencies ++= Seq(
  "twita" %% "dominion-api" % dominionVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion
)
