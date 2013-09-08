import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "simple"
  val appVersion      = "0.0.1-SNAPSHOT"
  val appDependencies = Seq(
    "play-stretchy" %% "play-stretchy" % "0.0.4"
  )

  val main = play.Project(appName, appVersion, appDependencies)
    .settings(
      stretchy.SbtStretchyPlugin.plugins("elasticsearch/elasticsearch-mapper-attachments/1.7.0") : _*
    )

//  val main = play.Project(appName, appVersion, appDependencies)
//    .settings(
//    stretchy.SbtStretchyPlugin.defaultSettings: _*
//  )
//    .settings(
//    esPlugins := Seq( "elasticsearch/elasticsearch-mapper-attachments/1.7.0", "blah")
//  )
}
