import Dependencies._

version := whipsawVersion
name := "whipsaw-workload-reactive-mongo-impl"
scalaVersion := scalaVsn

libraryDependencies ++= Seq(
  "io.github.thats-what-im-talking-about" %% "dominion-reactive-mongo-impl" % dominionVersion
)
