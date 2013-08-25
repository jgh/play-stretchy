package stretchy

import org.specs2.mutable._
import sbt._
/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 19/08/13
 * Time: 8:14 AM
 * To change this template use File | Settings | File Templates.
 */
class SbtStretchyPluginTest extends Specification {

  "Plugin" should {
    "install ES plugins" in {
       IO.withTemporaryDirectory(
        tempBase  =>  {
          val pluginDir: sbt.File = new File(tempBase, "plugins")
          val plugins  = Seq("one",  "two")
          IO.createDirectory(pluginDir)
          val props = """
                         |two=2222
                         |three=3333
                       """.stripMargin
          IO.write(new File(pluginDir,   SbtStretchyPlugin.pluginsFilename),props)
          IO.createDirectory(new File(pluginDir,  "2222"))
          IO.createDirectory(new File(pluginDir,  "3333"))
          IO.createDirectory(new File(pluginDir,  "4444"))
          new TestESPluginManager(pluginDir).syncPlugins(plugins)

          pluginDir.list().toSet mustEqual Set("2222", "es-one", SbtStretchyPlugin.pluginsFilename)
        }
      )
    }
  }

}

class TestESPluginManager(val pluginsDirectory:File) extends EsPluginManager  {
  def installPlugin(key: String) {
    val dir: sbt.File = new File(pluginsDirectory, "es-" + key)
    IO.createDirectory(dir)
    println("Created  "    + dir.name  + " for  "  +  key )
  }

  def removePlugin(name: String) {
     IO.delete(new File(pluginsDirectory, name))
    println("Removed  "     +  name )
  }
}
