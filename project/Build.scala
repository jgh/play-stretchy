import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "play-stretchy"
  val appVersion      = "0.0.2"


  val appDependencies = Seq(
    // Add your project dependencies here,
	"org.elasticsearch" % "elasticsearch" % "0.90.1"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}
