package stretchy

import org.specs2.mutable._
import sbt._
import org.specs2.control.Properties
import java.util

/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 19/08/13
 * Time: 8:14 AM
 * To change this template use File | Settings | File Templates.
 */
class SbtStretchyPluginTest extends Specification {

  "Plugin" should {
    "zip files" in {

      IO.withTemporaryDirectory(base => {
        val dirToZip = new File(base, "lib")
        IO.createDirectory(dirToZip)
        IO.write(dirToZip / "test1", "test")
        IO.write(dirToZip / "test2", "test")

        val zip: File = base / ("test.zip")
        IO.zip(allSubpaths(dirToZip), zip)

        val out: File = base / "out"
        IO.createDirectory(out)
        IO.unzip(zip,out) mustEqual(Set(out /"test1", out  / "test2"))
        IO.delete((out **  "*").get)
        //Create  a  zip containing this  lib  directory but  under  a  different directory in the zip
        val finder: PathFinder = dirToZip ** "*" --- dirToZip //Remove dirToZip as you  can't  rebase a directory  to  itself
        IO.zip(finder x rebase(dirToZip, "newlib"), base / "rebased.zip")

        IO.createDirectory(out)
        IO.unzip(base  /  "rebased.zip",out) mustEqual(Set(out /"newlib"/"test1", out  / "newlib"/ "test2"))
      })


    }
    "dist ES plugins" in {

      IO.withTemporaryDirectory(
        tempBase => {
          val pluginDir: sbt.File = new File(tempBase, "plugins")
          IO.createDirectory(pluginDir)
          val distDir: sbt.File = new File(tempBase, "dist")
          IO.createDirectory(distDir)
          val packageName: String = "test-1.0-SNAPSHOT"
          val testPlugin: sbt.File = new File(pluginDir, "1111")
          IO.createDirectory(testPlugin)
          val originalPluginFile = testPlugin / "test"
          IO.write(originalPluginFile, "test")

          val tempPackageDir: sbt.File = tempBase / "input" / packageName
          IO.createDirectory(tempPackageDir)
          val originalFile = tempPackageDir / "test.txt"
          IO.write(originalFile, "test")
          val originalZip: File = distDir / (packageName + ".zip")
          println("in  tempbase:   " + (tempBase ***))
          println("temp files  to  zip:  " + (tempPackageDir / packageName ***))
          val filesToZip = (tempPackageDir ** "*").x(relativeTo(tempPackageDir.getParentFile))
          println("Files  to  zip:  " + filesToZip)
          IO.zip(filesToZip, originalZip)
          SbtStretchyPlugin.distPlugins(tempBase, "test", "1.0-SNAPSHOT", pluginDir)

          println("Zipped  files:  " + IO.unzip(distDir / (packageName + ".zip"), tempBase))

          (tempBase / packageName / "plugins" / "1111" / "test").exists must_== true
          (tempBase / packageName / "test.txt").exists must_== true

        }
      )
    }
    "stage ES plugins" in {

      IO.withTemporaryDirectory(
        tempBase => {
          val targetDir = tempBase / "target"
          IO.createDirectory(targetDir)

          val pluginDir: sbt.File = new File(tempBase, "plugins")
          IO.createDirectory(pluginDir)
          val testPlugin: sbt.File = new File(pluginDir, "1111")
          IO.createDirectory(testPlugin)
          val originalPluginFile = testPlugin / "test"
          IO.write(originalPluginFile, "test")

          SbtStretchyPlugin.stagePlugins(targetDir, pluginDir)

          (targetDir / "plugins" / "1111" / "test").exists must_== true
        }
      )
    }
    "install ES plugins from scratch" in {
      IO.withTemporaryDirectory(
        tempBase => {
          val pluginDir: sbt.File = new File(tempBase, "plugins")
          val mappingFile: sbt.File = pluginDir  / SbtStretchyPlugin.pluginsFilename
          val plugins = Seq("one", "two")
          new TestESPluginManager(pluginDir).syncPlugins(plugins)

          pluginDir.list().toSet mustEqual Set("es-two", "es-one", SbtStretchyPlugin.pluginsFilename)

          val updatedProps = new util.Properties()
          IO.load(updatedProps, mappingFile)
          println(updatedProps)
          updatedProps.getProperty("one") mustEqual ("es-one")
          updatedProps.getProperty("two") mustEqual ("es-two")
        }
      )
    }
    "install ES plugins  with existing  plugins installed" in {
      IO.withTemporaryDirectory(
        tempBase => {
          val pluginDir: sbt.File = tempBase/ "plugins"
          val plugins = Seq("one", "two")
          IO.createDirectory(pluginDir)
          val props = """
                        |two=2222
                        |three=3333
                      """.stripMargin
          val mappingFile: sbt.File = pluginDir  / SbtStretchyPlugin.pluginsFilename
          IO.write(mappingFile, props)
          IO.createDirectory(new File(pluginDir, "2222"))
          IO.createDirectory(new File(pluginDir, "3333"))
          IO.createDirectory(new File(pluginDir, "4444"))
          new TestESPluginManager(pluginDir).syncPlugins(plugins)

          pluginDir.list().toSet mustEqual Set("2222", "es-one", SbtStretchyPlugin.pluginsFilename)

          val updatedProps = new util.Properties()
          IO.load(updatedProps, mappingFile)
          println(updatedProps)
          updatedProps.getProperty("one") mustEqual ("es-one")
          updatedProps.getProperty("three") mustEqual (null)
        }
      )
    }
  }

}

class TestESPluginManager(val pluginsDirectory: File) extends EsPluginManager {
  def installPlugin(key: String) {
    val dir: sbt.File = new File(pluginsDirectory, "es-" + key)
    IO.createDirectory(dir)
    println("Created  " + dir.name + " for  " + key)
  }

  def removePlugin(name: String) {
    IO.delete(new File(pluginsDirectory, name))
    println("Removed  " + name)
  }
}
