import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "simple"
  val appVersion      = "0.0.1-SNAPSHOT"
  val appDependencies = Seq(
    "play-stretchy" %% "play-stretchy" % "0.0.1"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
