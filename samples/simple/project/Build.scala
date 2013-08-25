import sbt._
import Keys._
import play.Project._
import stretchy.SbtStretchyPlugin._

object ApplicationBuild extends Build {

  val appName         = "simple"
  val appVersion      = "0.0.1-SNAPSHOT"
  val appDependencies = Seq(
    "play-stretchy" %% "play-stretchy" % "0.0.3-SNAPSHOT"
  )

  val main = play.Project(appName, appVersion, appDependencies)
    .settings(
      stretchy.SbtStretchyPlugin.pluginSettings : _*
    )
    .settings(
      esPlugins := Seq( "elasticsearch/elasticsearch-mapper-attachments/1.7.0")
    )
}
