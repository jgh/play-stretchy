package stretchy

import sbt._
import sbt.Keys._
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.plugins.PluginManager
import java.util.Properties
import collection.JavaConversions._
import org.elasticsearch.env.Environment

trait WithStretchyCommands extends play.PlayCommands  {
  override  val distTask =  (distDirectory, baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, normalizedName, version) map { (dist, root, packaged, dependencies, target, id, version) =>
    super.distTask()
  }
}
import sbt.PlayKeys._
object SbtStretchyPlugin extends Plugin
{
  // configuration points, like the built in `version`, `libraryDependencies`, or `compile`
  // by implementing Plugin, these are automatically imported in a user's `build.sbt`
  val installEsPlugins = TaskKey[Unit]("install-es-plugins")
  val dependsTest = TaskKey[Unit]("depends-test")
  val distWithEsPlugins = TaskKey[Unit]("dist-es-plugins-test")
  val esPluginDirectory = SettingKey[File]("es-plugin-directory")
  val esPlugins = SettingKey[Seq[String]]("es-plugins")


  val pluginsFilename: String = "plugins.properties"

  // a group of settings ready to be added to a Project
  // to automatically add them, do
  val pluginSettings = Seq(
    esPluginDirectory  <<= baseDirectory(base => new File(base,"plugins")),
    esPlugins  := Seq(),
    installEsPlugins <<= (esPluginDirectory, esPlugins, baseDirectory) map {
      (dir, plugins, base) =>  {

        installPlugins(dir, plugins, base)
      }
    },
    dependsTest <<= (baseDirectory) map {
      (base) =>  {
        println("****8" +
          "Depends" +
          "*******")
      }
    },
    playStage <<= (playStage, target, esPluginDirectory) map (stagePlugins)


  )

  def plugins(pluginKeys:Seq[String])  = {
    pluginSettings ++ (esPlugins := pluginKeys)
  }

  def stagePlugins(originalStage:Unit, target:File, plugins:File) = {
    val staged = target / "staged"
    IO.copyDirectory(plugins, staged)

  }

  def installPlugins(dir: File, plugins: Seq[String], base: File) {
    new EsPluginManager {
      val manager = {
        val builder = ImmutableSettings.builder()
          .put("plugin.home", dir.getAbsolutePath)
          .put("path.home", base.getAbsolutePath)
          .put("name", "dummy_node_" + System.currentTimeMillis())
          .put("node.local", "true")
        val environment = new Environment(builder.build());
        new PluginManager(environment, null)
      }
      def removePlugin(name: String) {
        manager.removePlugin(name)
      }

      def installPlugin(key: String) {
         manager.downloadAndExtract(key, true)
      }

      def pluginsDirectory = dir
    }.syncPlugins(plugins)

  }

}

trait EsPluginManager  {
  def pluginsDirectory:File
  def installPlugin(key:String)
  def removePlugin(name:String)

  val pluginsFilename: String = "plugins.properties"
  lazy val pluginMappings   = {
    val properties = new Properties()
    IO.load(properties, new File(pluginsDirectory, pluginsFilename))
    properties.stringPropertyNames().map(n => (n, properties.getProperty(n))).toMap
  }

  def getInstalledPluginNames(): Set[String] = {
    pluginsDirectory.listFiles().map(f => f.getName).toSet.filterNot(_ == pluginsFilename)
  }


  def syncPlugins(plugins: Seq[String]) {
    try {

      val existingPluginNames = getInstalledPluginNames()


      val  newKeys = plugins.filterNot(p => {
        pluginMappings.get(p).filter(existingPluginNames.contains(_)).isDefined
      })


      val namesToRetain =  plugins.flatMap(pluginMappings.get(_))
      println("names to  retain: "  + namesToRetain)
      println("existing names: "  + existingPluginNames)
      val pluginsToRemove  = existingPluginNames -- namesToRetain
      println("mapping:  " + pluginMappings)
      println("Plugins keys  defined:   " + plugins)
      println("New Keys:  "  +  newKeys)
      println("already installed:  " + existingPluginNames)
      println("PluginsToRemove:  " +pluginsToRemove)


      val remainingPluginNames = pluginsToRemove.foldLeft(existingPluginNames) ((names, toRemove)  => {
        removePlugin(toRemove)
        names - toRemove
      })

      newKeys.foreach(plugin => {
        installPlugin(plugin)
        println("plugin  installed  " + plugin)
      })
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
  }
}
