import Dependencies._

ThisBuild / organization := "twita"
ThisBuild / version := whipsawVersion
name := "whipsaw"

publishMavenStyle := false

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

lazy val api = project in file("api")

lazy val root = (project in file(".")).dependsOn(api).aggregate(api)
