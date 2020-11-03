import Dependencies._

version := whipsawVersion
name := "whipsaw-workload-reactive-mongo-impl"

libraryDependencies ++= Seq(
    "twita" %% "dominion-reactive-mongo-impl" % dominionVersion
)
