package stretchy

import sbt._
import sbt.Keys._
import sbt.Path.rebase
import sbt.Path.relativeTo
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.plugins.PluginManager
import java.util.Properties
import collection.JavaConversions._
import org.elasticsearch.env.Environment

//trait WithStretchyCommands extends play.PlayCommands {
// override val distTask = (distDirectory, baseDirectory, playPackageEverything, dependencyClasspath in Runtime, target, normalizedName, version) map { (dist, root, packaged, dependencies, target, id, version) =>
//  super.distTask()
// }
//}

//import sbt.PlayKeys._

object SbtStretchyPlugin extends Plugin {
 // configuration points, like the built in `version`, `libraryDependencies`, or `compile`
 // by implementing Plugin, these are automatically imported in a user's `build.sbt`
 val installEsPlugins = TaskKey[File]("install-es-plugins", "Install all plugins defined in the es-plugins settings. Plugins no longer defined there will be removed")
 val removeEsPlugins = TaskKey[File]("remove-es-plugins", "Removes all ElasticSearch plugins.")
 val stageEsPlugins = TaskKey[File]("stage-es-plugins", "Copies ElasticSearch plugins to staging directory")
 val distEsPlugins = TaskKey[File]("dist-es-plugins", "Adds ElasticSearch plugins to distribution zip file.")
 val esPluginDirectory = SettingKey[File]("es-plugin-directory", "The directory plugins will be installed into. Defaults to baseDirectory/plugins. ")
 val esPlugins = SettingKey[Seq[String]]("es-plugins", "ElasticSearch plugins to be installed. The keys used here match what is used in the ES plugin script.")
 val packageName = SettingKey[String]("package-name", "Distribution package name. Defaults to the normalizedName-version. This is used to generate the dist zip file name and the directory name within the zip" )
 val distFile = SettingKey[File]("dist-file", "The distribution zip file")

 val pluginsFilename: String = "plugins.properties"

 // a group of settings ready to be added to a Project
 // to automatically add them, do
 val defaultSettings:Seq[Project.Setting[_]] = Seq(
  esPluginDirectory <<= baseDirectory(base => base / "plugins"),
  installEsPlugins <<= (esPluginDirectory, esPlugins, baseDirectory) map (installPlugins),
  removeEsPlugins <<= (esPluginDirectory, esPlugins, baseDirectory) map (removeAllPlugins),
  stageEsPlugins <<= (target, installEsPlugins) map (stagePlugins),
  distEsPlugins <<= (baseDirectory, normalizedName, version, installEsPlugins) map (distPlugins),
  packageName <<= (normalizedName, version) (generatePackageName),
  distFile <<= (baseDirectory, packageName) (generateDistFile)

 )

 def plugins(keys: String*): Seq[Project.Setting[_]] = {
  val pluginsSetting: Project.Setting[_] = esPlugins := keys
  defaultSettings ++ Seq(pluginsSetting)
 }

 def generatePackageName(normalizedName:String, version:String): String = normalizedName + "-" + version
 def generateDistFile (baseDirectory:File, packageName:String):File = { baseDirectory / "dist" / (packageName + ".zip") }

 def stagePlugins(target: File, plugins: File) = {
  val targetPlugins = target / plugins.getName
  IO.delete(targetPlugins)
  IO.copyDirectory(plugins, targetPlugins )
  targetPlugins
 }

 def distPlugins(base: File, normalizedName: String, version: String, plugins: File) = {
  val packageName: String = normalizedName + "-" + version
  val distFile = base / "dist" / (packageName + ".zip")
  if (distFile.exists()) {
   if ((plugins * "*").get.isEmpty) {
    println("No plugins found in: " + plugins)
   } else {
    IO.withTemporaryDirectory(temp => {
     IO.unzip(distFile, temp)
     val distDir = temp / packageName
     if (distDir.exists) {
      val pluginFiles = (plugins ** "*").x(rebase(base, packageName))
      val originalFiles = allSubpaths(temp)
      IO.zip(pluginFiles ++ originalFiles, distFile)
      println("Updated dist zip: " + distFile)
     } else {
      println("Did not find " + packageName)
     }
    })
   }
  } else {
   println("Dist file " + distFile.getAbsolutePath + " not found. Run the dist task first.")
  }
  distFile
 }

 def installPlugins(dir: File, plugins: Seq[String], base: File) = {
  createPluginManager(dir, plugins, base).syncPlugins(plugins)
  dir
 }

 def removeAllPlugins(dir: File, plugins: Seq[String], base: File) = {
  createPluginManager(dir, plugins, base).removeAllPlugins()
  dir
 }

 def createPluginManager(dir: File, plugins: Seq[String], base: File) = {
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
  }
 }

}

trait EsPluginManager {
 def pluginsDirectory: File

 def installPlugin(key: String)

 def removePlugin(name: String)

 val pluginsFilename: String = "plugins.properties"
 lazy val pluginMappings = {
  val properties = new Properties()
  IO.load(properties,
   (pluginsDirectory / pluginsFilename))
  properties.stringPropertyNames().map(n => (n, properties.getProperty(n))).toMap
 }

 def getInstalledPluginNames(): Set[String] = {
  if(pluginsDirectory.exists()) {
   pluginsDirectory.listFiles().map(f => f.getName).toSet.filterNot(_ == pluginsFilename)
  } else {
   Set()
  }
 }

 def removeAllPlugins() {
   getInstalledPluginNames().foreach(p => {
    removePlugin(p)
    println("Removed  plugin: '" + p + "'")

   })
   IO.delete(pluginsDirectory / pluginsFilename)
 }

 def syncPlugins(plugins: Seq[String]) {
  try {

   println("Plugin keys defined:  " + plugins.map("'"+_+"'").mkString(", "))
   val existingPluginNames = getInstalledPluginNames()


   val newKeys = plugins.filterNot(p => {
    pluginMappings.get(p).filter(existingPluginNames.contains(_)).isDefined
   })


   val namesToRetain = plugins.flatMap(pluginMappings.get(_))
   val pluginsToRemove = existingPluginNames -- namesToRetain
//   println("names to retain: " + namesToRetain)
//   println("existing names: " + existingPluginNames)
//   println("Plugins keys defined:  " + plugins)
//   println("New Keys: " + newKeys)
//   println("already installed: " + existingPluginNames)
//   println("PluginsToRemove: " + pluginsToRemove)


   val remainingInstalledPluginNames = pluginsToRemove.foldLeft(existingPluginNames)((names, toRemove) => {
    removePlugin(toRemove)
    println("Removed  plugin: '" + toRemove +"'")
    names - toRemove
   })

   val remainingPluginMapping = pluginMappings.filter(e => {
     if (remainingInstalledPluginNames.contains( e._2)) {
      println("Plugin '" + e._1 + "' already installed in '"  + e._2 + "'")
      true
     } else  {
      false
     }
   })
   val newMappings = newKeys.foldLeft(remainingPluginMapping)((pluginMappings, plugin) => {
    val currentPlugins = (pluginsDirectory * "*").get
    try  {

      installPlugin(plugin)
    } catch  {
      case e: Exception => {
        println("Installation  of '" + plugin +  "'  failed:  "  +  e.getMessage)
        throw e
      }
    }
    val newPlugins = (pluginsDirectory * "*").get.toSet -- currentPlugins
    if (newPlugins.isEmpty) {
     println("Plugin '" + plugin + "' installed but no directory was added to the plugins directory ")
     pluginMappings
    } else {
     println("Plugin '" + plugin + "' installed in '"  + newPlugins.head.getName +"'")
     pluginMappings + (plugin -> newPlugins.head.getName)

    }
   })

   val props = newMappings.foldLeft(new Properties()) ((props, e) => {
    props.setProperty(e._1, e._2)
    props
   })
   IO.write(props, "Mapping of installation key to plugin directory for ElasticSearch plugins",pluginsDirectory / pluginsFilename)
  } catch {
   case e: Exception => {
    e.printStackTrace()
    throw e
   }
  }
 }
}
