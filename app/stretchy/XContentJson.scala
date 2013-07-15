package stretchy

import org.elasticsearch.common.xcontent.ToXContent
import play.api.libs.json.{Json, Writes}
import org.elasticsearch.common.xcontent.json.JsonXContent

/**
 * Created with IntelliJ IDEA.
 * User: jeremy
 * Date: 15/07/13
 * Time: 8:03 AM
 * To change this template use File | Settings | File Templates.
 */
object XContentJson {

  implicit object XContentWrites extends Writes[ToXContent] {
    def writes(o: ToXContent) = {
      val builder = JsonXContent.contentBuilder()
      builder.startObject()
      o.toXContent(builder, ToXContent.EMPTY_PARAMS)
      builder.endObject()
      val jsonString = builder.bytes.toUtf8
      Json.parse(jsonString)
    }
  }

}
