package stretchy
import com.typesafe.config.ConfigRenderOptions
import org.elasticsearch.node.NodeBuilder._
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.indices.{IndexMissingException, IndexAlreadyExistsException}
import play.api.{Application,Logger, Plugin, Configuration}
import org.elasticsearch.action.{ActionRequestBuilder, ActionResponse}
import scala.concurrent.Future
import org.elasticsearch.client.Client
import collection.JavaConversions._
/**
 * Created with IntelliJ IDEA.
 * User: harej
 * Date: 13/06/13
 * Time: 8:56 AM
 * To change this template use File | Settings | File Templates.
 */
class ESPlugin (app: play.api.Application) extends  Plugin  {

  val log = Logger("application." + this.getClass.getSimpleName)
//  lazy val node = nodeBuilder().local(true).data(true).node()
  lazy val (client,closer) = ConfigClientBuilder(app.configuration)
  override def onStart() {
    new SetupIndices(app.configuration, client).execute()
  }
  override def onStop() {
    closer()
    log.info("Closed client")
  }
}
object ES {
  private def error = throw new Exception("ES plugin is not registered.")

  def executeActionRequest[Rs <: ActionResponse](request: ActionRequestBuilder[_, Rs, _]): Future[Rs] = {
    val listener = new PromiseActionListener[Rs]()
    request.execute(listener)
    listener.promise.future
  }

  def execute[Rs <: ActionResponse](block: Client  => ActionRequestBuilder[_, Rs, _])(implicit app: Application): Future[Rs] = {
    app.plugin[ESPlugin].map(es  =>  {
      executeActionRequest(block(es.client))
    }).getOrElse(error)
  }
  def withClient[Rs](block: Client  => Rs)(implicit app: Application): Rs = {
    app.plugin[ESPlugin].map(es  =>  {
      block(es.client)
    }).getOrElse(error)
  }
}


