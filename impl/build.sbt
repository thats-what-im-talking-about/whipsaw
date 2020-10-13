import Dependencies._

version := whipsawVersion
name := "whipsaw-impl"

libraryDependencies ++= Seq(
    "twita" %% "dominion-reactive-mongo-impl" % dominionVersion
)
