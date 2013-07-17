package stretchy

import org.elasticsearch.common.xcontent.ToXContent
import play.api.mvc.Codec
import play.api.http.{ContentTypes, Writeable}
import org.elasticsearch.common.xcontent.json.JsonXContent

/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 17/07/13
 * Time: 5:33 PM
 * To change this template use File | Settings | File Templates.
 */
object XContentWriteable {

  implicit def writeableOf_ToXContent[C <:   ToXContent ](implicit codec: Codec): Writeable[C] = {
    Writeable(txc =>  {
      val builder = JsonXContent.contentBuilder()
      builder.startObject()
      txc.toXContent(builder, ToXContent.EMPTY_PARAMS)
      builder.endObject()
      builder.bytes.toBytes()
    }, Some(ContentTypes.JSON))
  }
}
