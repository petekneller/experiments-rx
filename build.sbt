organization := "com.github.petekneller"

name := "experiments-rx"

version := "dev"

scalaVersion := "2.11.5"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "io.reactivex" %% "rxscala" % "0.23.1",
  "org.scalaz" %% "scalaz-core" % "7.1.1",
  "org.scalaz.stream" %% "scalaz-stream" % "0.6a",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)
