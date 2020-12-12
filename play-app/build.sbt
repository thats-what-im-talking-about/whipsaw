name := """play-scala-starter-example"""

libraryDependencies ++= Seq(
  guice,
  "com.h2database" % "h2" % "1.4.199",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
),
