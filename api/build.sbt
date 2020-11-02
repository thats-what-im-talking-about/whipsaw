import Dependencies._

name := "whipsaw-api"

libraryDependencies ++= Seq(
    "twita" %% "dominion-api" % dominionVersion
  , "com.typesafe.akka" %% "akka-stream" % akkaVersion
)
