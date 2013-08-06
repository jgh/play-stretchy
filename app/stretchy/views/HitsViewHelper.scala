package stretchy.views

import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.SearchHit
import scala.collection.JavaConversions._
import play.api.templates.{HtmlFormat, Html}
import org.elasticsearch.common.text.Text

case class HitsViewHelper(searchResponse:SearchResponse) {
  lazy val hits:Seq[HitViewHelper]  =  searchResponse.getHits.map( HitViewHelper(_)).toSeq
}

case  class HitViewHelper(hit:SearchHit)  {
  def id = hit.id()
  def hitType = hit.`type`()

  def apply[T](fieldName:String, default: T):T =  apply(fieldName).getOrElse(default)

  def apply[T](fieldName:String):Option[T] =  {
    val  field   = hit.field(fieldName)
    if(field  !=  null)  {
      Option(field.getValue[T])
    }  else  {
      None
    }
  }


  def withValue[T](fieldName:String) (body: T => Html):Html =  withValueOrElse(fieldName) (body) (Html.empty)

  def withValueOrElse[T](fieldName:String) (body: T => Html) (orElse:Html = Html.empty):Html =  {
    apply[T](fieldName)
      .map(body)
      .getOrElse(orElse)
  }

  def withHighlightedFragments  (body: Html => Html):Html  =  {
    fold(hit.highlightFields().values().flatMap(field  =>  {
      field.fragments().map(frag => body(HtmlFormat.raw(frag.string)))
    }))
  }

  def withHighlightFields  (body: (String, Iterable[Html]) => Html):Html  =  {
    fold(hit.highlightFields().values().map(field  =>  {
      val fragments = field.fragments().map(frag => HtmlFormat.raw(frag.string))
      body(field.getName, fragments)
    }))
  }
  def  fold(fragments:Iterable[Html]):Html = fragments.fold(Html.empty)((a,b)  => a  +=  b)
}