package stretchy

import play.api.{Logger, Configuration}
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.client.Client
import org.elasticsearch.common.transport.InetSocketTransportAddress
import collection.JavaConversions._

/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 13/06/13
 * Time: 4:00 PM
 * To change this template use File | Settings | File Templates.
 */
object ConfigClientBuilder {

  import org.elasticsearch.node.NodeBuilder._

  val log = Logger("application." + this.getClass.getSimpleName)


  def buildSettings(configuration: Option[Configuration]) = {
    configuration.map(config => {
      val builder = ImmutableSettings.builder()
      config.keys.map(key => (key, config.getString(key).get)).foreach(t => builder.put(t._1, t._2))
      builder
    }).getOrElse(ImmutableSettings.builder())
  }

  def apply(config: Configuration): (Client, () => Unit) = {
    val client = config.getConfig("elasticsearch.client").get

    val clusterName = client.getString("clusterName")
    client.getConfig("transport").map(transport => {
      buildTransportClient(transport, clusterName)
    }).getOrElse(buildNodeClient(client, clusterName))
  }


  def buildTransportClient(transport: Configuration, clusterName: Option[String]): (Client,  () => Unit) = {
    val settings = buildSettings(transport.getConfig("settings"))
    clusterName.foreach(settings.put("cluster.name", _))

    val addresses = transport.getStringList("transportAddresses").get.map(add => {
      val hostPort = add.split(":")
      new InetSocketTransportAddress(hostPort(0), hostPort(1).toInt)
    })
    val client = new TransportClient(settings).addTransportAddresses(addresses: _*)

    log.info("Created transport client connecting to addresses: " + addresses)

    (client, client.close)
  }

  def buildNodeClient(client: Configuration, clusterName: Option[String]): (Client, () => Unit) = {
    val builder = nodeBuilder()
    client.getConfig("node").foreach(node => {
      node.getBoolean("local").foreach(builder.local(_))
      node.getBoolean("data").foreach(builder.data(_))
      val loadConfig = node.getBoolean("loadConfigSettings")
      loadConfig.foreach(builder.loadConfigSettings(_))

      val settings = buildSettings(node.getConfig("settings")).build()

//      val initialSettings = InternalSettingsPerparer.prepareSettings(settings, loadConfig.getOrElse(true) );
//      val manager = new PluginManager(initialSettings.v2(),  null)
//      manager.downloadAndExtract( "elasticsearch/elasticsearch-mapper-attachments/1.7.0",  true)
//      println("plugin  installed")
      builder.settings(settings)
    })
    clusterName.foreach(builder.clusterName(_))

    log.info("Created node client. connecting to cluster: " + clusterName)
    val node = builder.node()

    (node.client, node.close)

  }
}
