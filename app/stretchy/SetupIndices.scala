package stretchy

import play.api.{Logger, Configuration}
import org.elasticsearch.client.Client
import com.typesafe.config.ConfigRenderOptions
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.indices.{IndexMissingException, IndexAlreadyExistsException}
import collection.JavaConversions._

/**
 * Setups up indicies on the node  behind  client based  on  the  config.
 * All calls  to ES  are   blocking
 * @param config
 * @param client
 */
class SetupIndices(config:Configuration, client:Client) {
  //todo: Switch to plain config  so  can be used from akka

  val log = Logger("application." + this.getClass.getSimpleName)
  def execute() {
    val indices = config.getConfigList("elasticsearch.indicies").get
    indices.foreach(processIndex)
  }

  def processIndex(config:Configuration)  {
    val  indexName = config.getString("name").get
    if (config.getBoolean("deleteIndex").getOrElse(false)) {
      deleteIndex(indexName)
    }

    if (config.getBoolean("createIndex").getOrElse(true)) {
      createIndex(indexName)
    }


    val mappings = config.getConfig("mappings").get
    mappings.subKeys.foreach(key =>{
//      val mapping = mappings.underlying.root().render(ConfigRenderOptions.concise())
      val mapping =  mappings.getConfig(key).get.underlying.root().render(ConfigRenderOptions.concise())

      val  source  = "{%s: %s}".format(key,mapping)
      println("Mapping:  "  +  source)
      client.admin().indices().preparePutMapping(indexName).setType(key).setSource(source).execute().actionGet().isAcknowledged
      logInfo(indexName,"Created  a mapping  for  type %s".format( key))
    })
//    println("mappings:  "  + mappings.underlying.root().render())
//    client.admin().indices().preparePutMapping(indexName).setType(logType).setSource(Json.stringify(logMapping)).executeActionRequest().actionGet().isAcknowledged
  }


  def logInfo(indexName: String, message: String) {
    log.info("[Index: %s] %s".format(indexName, message))
  }

  def createIndex(indexName: String) {
    try {
      client.admin().indices().prepareCreate(indexName).setSettings(
        ImmutableSettings.settingsBuilder()
          .put("number_of_shards", 1)
          .put("index.numberOfReplicas", 1))
        .execute().actionGet()
      logInfo(indexName,"Created.")


    } catch {
      case e: IndexAlreadyExistsException => {
        logInfo(indexName,"Index  was  already  created.")
      }
    }
  }

  def deleteIndex(indexName: String) {
    try {
      client.admin().indices().prepareDelete(indexName).execute().get()
      logInfo(indexName,"Deleted.")
    } catch {
      case e: IndexMissingException => {
        logInfo(indexName,"Index doesn't  exist")
      }
      case e: Exception => {
        logInfo(indexName,"Error occurred deleting  index")
        log.info("",e)
      }
    }
  }


}
