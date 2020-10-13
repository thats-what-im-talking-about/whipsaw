import Dependencies._

version := whipsawVersion
name := "whipsaw-impl"

libraryDependencies ++= Seq(
    "twita" %% "dominion-reactive-mongo-impl" % dominionVersion
  , "org.scalactic" %% "scalactic" % "3.2.0"
  , "org.scalatest" %% "scalatest" % "3.2.0" % "test"
  , "org.slf4j" % "log4j-over-slf4j" % "1.7.30" % "test"
)
