import Dependencies._

version := whipsawVersion
name := "whipsaw-workload-reactive-mongo-impl"
scalaVersion := scalaVsn

libraryDependencies ++= Seq(
  "twita" %% "dominion-reactive-mongo-impl" % dominionVersion
)
