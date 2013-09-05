sbtPlugin := true

name := "sbt-stretchy"

organization := "play-stretchy"

libraryDependencies +=  "org.elasticsearch" % "elasticsearch" % "0.90.1"

version := "0.0.4-SNAPSHOT"

//addSbtPlugin("play" % "sbt-plugin" % "2.1.1")

libraryDependencies +=       "org.specs2" %% "specs2" % "1.9" % "test"

