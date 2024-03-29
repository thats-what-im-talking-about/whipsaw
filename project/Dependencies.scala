import sbt._

/**
  * This file contains the versions of our various dependencies that we need to share
  * across all of our builds.  For additional background and documentation on what
  * may be included here, see:
  *
  * https://www.scala-sbt.org/1.x/docs/Organizing-Build.html
  */
object Dependencies {

  // ----------------------------------------------------------------------
  //
  //        V   E   R   S   I   O   N   S
  //
  // ----------------------------------------------------------------------
  lazy val scalaVsn = "2.13.2"
  lazy val dominionVersion = "0.1.8"
  lazy val akkaVersion = "2.6.10"
}
